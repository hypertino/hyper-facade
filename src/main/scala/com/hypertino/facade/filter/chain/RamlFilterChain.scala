package com.hypertino.facade.filter.chain

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{ContentType, Method, RamlConfiguration, RamlResourceMethodConfig}
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}

class RamlFilterChain(ramlConfig: RamlConfiguration) extends FilterChain {

  def findRequestFilters(contextWithRequest: ContextWithRequest): Seq[RequestFilter] = {
    val request = contextWithRequest.request
    val filters = requestOrEventFilters(request.headers.hrl.location, request.headers.method, request.headers.contentType).requestFilters
    filters
  }

  def findResponseFilters(context: ContextWithRequest, response: DynamicResponse): Seq[ResponseFilter] = {
    val method = context.originalHeaders.method
    val result = filtersOrMethod(context.originalHeaders.hrl.location, method) match {
      case Left(filters) ⇒
        filters

      case Right(resourceMethod) ⇒
        resourceMethod.responses.get(response.headers.statusCode) match {
          case Some(responses) ⇒
            responses.ramlContentTypes.get(response.headers.contentType.map(ContentType)) match {
              // todo: test this!
              case Some(ramlContentType) ⇒
                ramlContentType.filters
              case None ⇒
                resourceMethod.methodFilters
            }
          case None ⇒
            resourceMethod.methodFilters
        }
    }
    result.responseFilters
  }

  def findEventFilters(context: ContextWithRequest, event: DynamicRequest): Seq[EventFilter] = {
    val uri = context.originalHeaders.hrl.location // event.uri.pattern.specific
    requestOrEventFilters(uri, event.headers.method, event.headers.contentType).eventFilters
  }

  private def requestOrEventFilters(uri: String, method: String, contentType: Option[String]): SimpleFilterChain = {
    filtersOrMethod(uri, method) match {
      case Left(filters) ⇒ filters
      case Right(resourceMethod) ⇒
        resourceMethod.requests.ramlContentTypes.get(contentType.map(ContentType)) match {
          case Some(ramlContentType) ⇒
            ramlContentType.filters
          case None ⇒
            resourceMethod.methodFilters
        }
    }
  }

  private def filtersOrMethod(uri: String, method: String): Either[SimpleFilterChain, RamlResourceMethodConfig] = {
    ramlConfig.resourcesByPattern.get(uri) match {
      case Some(resource) ⇒
        resource.methods.get(Method(method)) match {
          case Some(resourceMethod) ⇒
            Right(resourceMethod)
          case None ⇒
            Left(resource.filters)
        }
      case None ⇒
        Left(FilterChain.empty)
    }
  }
}
