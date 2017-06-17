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

  def findResponseFilters(context: FacadeRequestContext, response: DynamicResponse): Seq[ResponseFilter] = {
    context.preparedHeaders match {
      case Some(r) ⇒
        val method = r.method
        val result = filtersOrMethod(r.hrl.location, method) match {
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

      case None ⇒
        Seq.empty
    }
  }

  def findEventFilters(context: FacadeRequestContext, event: DynamicRequest): Seq[EventFilter] = {
    context.preparedHeaders match {
      case Some(r) ⇒
        val uri = r.hrl.location // event.uri.pattern.specific
        requestOrEventFilters(uri, event.headers.method, event.headers.contentType).eventFilters

      case None ⇒
        Seq.empty
    }
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
    ramlConfig.resourcesByUri.get(uri) match {
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
