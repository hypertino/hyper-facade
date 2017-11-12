/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.before_resolved

import java.net.MalformedURLException

import com.hypertino.binders.value.Text
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.serialization.JsonContentTypeConverter
import com.hypertino.hyperbus.util.SeqGenerator
import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Scheduler

class HttpWsRequestFilter(config: Config, ramlConfig: RamlConfiguration,
                          protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    Task.now {
      try {
        val request = requestContext.request
        //val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
        // val uriTransformer = chain(removeRootPathPrefix(ramlConfig.baseUri, _: HRL), rewriteLinkForward(_: HRL, rewriteCountLimit, ramlConfig))

        val uri = spray.http.Uri(request.headers.hrl.location)
        val hrl = HRL(uri.path.toString, request.headers.hrl.query)

        val headersBuilder = MessageHeaders.builder
        var messageIdFound = false

        requestContext.httpHeaders.foreach { kv ⇒
          (kv._1, kv._2) match {
            case (FacadeHeaders.CONTENT_TYPE, value) ⇒
              JsonContentTypeConverter.universalJsonContentTypeToSimple(value) match {
                case Text(contentType) ⇒ headersBuilder.withContentType(Some(contentType))
                case _ ⇒ // ...
              }

            case (Header.MESSAGE_ID, value) if !value.isEmpty ⇒
              headersBuilder.withMessageId(value.toString)
              messageIdFound = true

            case (k, v) ⇒
              if (FacadeHeaders.directHeaderMapping.contains(k)) {
                headersBuilder += k → v
              }
          }
        }

        if (!messageIdFound) {
          headersBuilder += Header.MESSAGE_ID → SeqGenerator.create()
        }

        headersBuilder.withHRL(hrl)

        //todo: HAL ?
        //val transformedBodyContent = HalTransformer.transformEmbeddedObject(request.body.content, uriTransformer)

        requestContext.copy(
          request = DynamicRequest(request.body, headersBuilder.requestHeaders())
        )
      } catch {
        case e: MalformedURLException ⇒
          implicit val mcx = requestContext.request
          throw NotFound(ErrorBody("not-found", Some(e.getMessage)))
      }
    }
  }
}
