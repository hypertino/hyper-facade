package com.hypertino.facade.filter.http

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value._
import com.hypertino.facade.filter.model.{EventFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HrlTransformer._
import com.hypertino.hyperbus.model.{DefLink, DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, HRL, Header, Headers, HeadersBuilder, HeadersMap, Message, RequestHeaders, ResponseBase, ResponseHeaders, StandardResponse}
import scaldi.Injector
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config,
                           protected val predicateEvaluator: PredicateEvaluator) extends ResponseFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      //todo: implement rewriting back
      //val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      //val uriTransformer = chain(rewriteLinkToOriginal(_: HRL, rewriteCountLimit), addRootPathPrefix(rootPathPrefix))
      val (body, headersObj) = HttpWsFilter.filterMessage(response, hrl ⇒ hrl)
      StandardResponse(body, ResponseHeaders(headersObj)).asInstanceOf[DynamicResponse]
    }
  }
}

class WsEventFilter(config: Config, ramlConfig: RamlConfiguration,
                    protected val predicateEvaluator: PredicateEvaluator) extends EventFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)
  override def apply(contextWithRequest: ContextWithRequest, request: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      //val uriTransformer = chain(rewriteLinkToOriginal(_: HRL, rewriteCountLimit), addRootPathPrefix(ramlConfig.baseUri))
      val (newBody, newHeaders) = HttpWsFilter.filterMessage(request, hrl ⇒ hrl) // todo: root/baseUri
      val n = Headers
        .builder
        .++=(newHeaders)
        //.withHRL(addRootPathPrefix(rootPathPrefix)(request.headers.hrl)) // todo: root/baseUri
        .withHRL(request.headers.hrl)
        .result()
      DynamicRequest(newBody, RequestHeaders(n))
    }
  }
}

object HttpWsFilter {
  def filterMessage(message: DynamicMessage, uriTransformer: (HRL ⇒ HRL)): (DynamicBody, HeadersMap) = {
    val headersBuilder = Headers.builder

    message.headers.foreach {
      // todo: transform?
      //case (Header.LOCATION, v) ⇒
      //case (Header.HRL, v) ⇒
      case (k, v) ⇒
        if (FacadeHeaders.directHeaderMapping.contains(k)) {
          headersBuilder += k → v
        }
    }

    // todo: move this to HalFilter
    //val newBodyContent = HalTransformer.transformEmbeddedObject(message.body.content, uriTransformer)

    /*if (newBodyContent.isInstanceOf[Obj] /* && response.status == 201*/ ) {
      // Created, set header value
      newBodyContent.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
        case Some(Left(l)) ⇒
          val newHref = Uri(l.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case Some(Right(la)) ⇒
          val newHref = Uri(la.head.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case _ ⇒
      }
    }*/

    (message.body, headersBuilder.result())
  }
}