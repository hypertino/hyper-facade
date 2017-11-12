/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.raml

import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model.{MethodTarget, RamlFilterFactory, ResourceTarget}
import com.typesafe.scalalogging.StrictLogging
import scaldi.{Injectable, Injector, StringIdentifier}

import scala.util.control.NonFatal

class RamlConfigFiltersInjector(resourcesByUri: Map[String, RamlResource])(implicit inj: Injector) extends Injectable with StrictLogging {
  val resourcesWithFilters = Map.newBuilder[String, RamlResource]

  def withResourceFilters(): Map[String, RamlResource] = {
    resourcesWithFilters ++= resourcesByUri
    resourcesByUri.foreach { uriToConfig ⇒
      val (uri, resourceConfig) = uriToConfig
      resourcesWithFilters += uri → injectResourceFilters(uri, resourceConfig)
    }
    resourcesWithFilters.result()
  }

  def injectResourceFilters(uri: String, resourceConfig: RamlResource): RamlResource = {
    val resourceFilters = createFilters(uri, None, resourceConfig.annotations)
    val resourceMethodsAcc = Map.newBuilder[Method, RamlResourceMethod]
    resourceConfig.methods.foreach { ramlResourceMethod ⇒
      val (method, resourceMethodConfig) = ramlResourceMethod
      resourceMethodsAcc += method → injectMethodFilters(uri, method, resourceMethodConfig, resourceFilters)
    }
    resourceConfig.copy(
      methods = resourceMethodsAcc.result(),
      filters = resourceFilters
    )
  }

  def injectMethodFilters(uri: String, method: Method, resourceMethodConfig: RamlResourceMethod, resourceFilters: SimpleFilterChain): RamlResourceMethod = {
    val methodFilterChain = resourceFilters ++ createFilters(uri, Some(method.name), resourceMethodConfig.annotations)
    val updatedRequests = injectRequestsFilters(resourceMethodConfig.requests, methodFilterChain)
    val updatedResponses = injectResponsesFilters(resourceMethodConfig.responses, methodFilterChain)
    resourceMethodConfig.copy(
      methodFilters = methodFilterChain,
      requests = updatedRequests,
      responses = updatedResponses
    )
  }

  def injectRequestsFilters(requests: RamlRequests, parentFilters: SimpleFilterChain): RamlRequests = {
    val updatedContentTypesConfig = injectContentTypeConfigFilters(requests.ramlContentTypes, parentFilters)
    requests.copy(
      ramlContentTypes = updatedContentTypesConfig
    )
  }

  def injectResponsesFilters(responseMap: Map[Int, RamlResponses], parentFilters: SimpleFilterChain): Map[Int, RamlResponses] = {
    val updatedResponseMap = Map.newBuilder[Int, RamlResponses]
    responseMap.foreach {
      case (responseCode, responses) ⇒
        val updatedContentTypesConfig = injectContentTypeConfigFilters(responses.ramlContentTypes, parentFilters)
        val updatedResponses = responses.copy(
          ramlContentTypes = updatedContentTypesConfig
        )
        updatedResponseMap += responseCode → updatedResponses
    }
    updatedResponseMap.result()
  }

  def injectContentTypeConfigFilters(contentTypesConfig: Map[Option[ContentType], RamlContentTypeConfig],
                                     parentFilters: SimpleFilterChain): Map[Option[ContentType], RamlContentTypeConfig] = {
    val updatedRequests = Map.newBuilder[Option[ContentType], RamlContentTypeConfig]
    contentTypesConfig.foreach {
      case (contentType, ramlContentTypeConfig) ⇒
        val updatedFilters = ramlContentTypeConfig.filterChain ++ parentFilters
        val updatedContentTypeConfig = ramlContentTypeConfig.copy(
          filterChain = updatedFilters
        )
        updatedRequests += contentType → updatedContentTypeConfig
    }
    updatedRequests.result()
  }

  private def createFilters(uri: String, method: Option[String], annotations: Seq[RamlAnnotation]): SimpleFilterChain = {
    annotations.foldLeft(FilterChain.empty) { (filterChain, annotation) ⇒
      val target = method match {
        case Some(m) ⇒ MethodTarget(uri, m, annotation)
        case None ⇒ ResourceTarget(uri, annotation)
      }

      try {
        val ident = StringIdentifier(annotation.name)
        inj.getBinding(List(ident)) match {
          case Some(_) ⇒
            val filterFactory = inject[RamlFilterFactory](annotation.name)
            filterChain ++ filterFactory.createFilterChain(target)

          case None ⇒
            logger.warn(s"Annotation '${annotation.name}' is not bound")
            filterChain
        }
      }
      catch {
        case NonFatal(e) ⇒
          logger.error(s"Can't inject filter for $annotation", e)
          filterChain
      }
    }
  }
}

class InvalidRamlConfigException(message: String) extends Exception(message)
