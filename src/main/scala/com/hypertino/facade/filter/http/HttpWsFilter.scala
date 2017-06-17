package com.hypertino.facade.filter.http

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value._
import com.hypertino.facade.filter.model.{EventFilter, ResponseFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HalTransformer
import com.hypertino.facade.utils.HrlTransformer._
import com.hypertino.hyperbus.model.{DefLink, DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, HRL, Header, HeadersBuilder, HeadersMap, Message, RequestHeaders, ResponseBase, ResponseHeaders, StandardResponse}
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config) extends ResponseFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: ContextWithRequest, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = chain(rewriteLinkToOriginal(_: HRL, rewriteCountLimit), addRootPathPrefix(rootPathPrefix))
      val (body, headersObj) = HttpWsFilter.filterMessage(response, uriTransformer)
      StandardResponse(body, ResponseHeaders(headersObj)).asInstanceOf[DynamicResponse]
    }
  }
}

class WsEventFilter(config: Config) extends EventFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)
  override def apply(contextWithRequest: ContextWithRequest, request: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = chain(rewriteLinkToOriginal(_: HRL, rewriteCountLimit), addRootPathPrefix(rootPathPrefix))
      val (newBody, newHeaders) = HttpWsFilter.filterMessage(request, uriTransformer)
      val n = new HeadersBuilder()
        .++=(newHeaders)
        .withHRL(addRootPathPrefix(rootPathPrefix)(request.headers.hrl))
        .result()
      DynamicRequest(newBody, RequestHeaders(n))
    }
  }
}

object HttpWsFilter {
  val directHyperbusToFacade = FacadeHeaders.clientHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap

  def filterMessage(message: DynamicMessage, uriTransformer: (HRL ⇒ HRL)): (DynamicBody, HeadersMap) = {
    val headersBuilder = HeadersMap.builder

    message.headers.foreach {
      // todo: transform?
      //case (Header.LOCATION, v) ⇒
      //case (Header.HRL, v) ⇒
      case (k, v) ⇒
        if (directHyperbusToFacade.contains(k)) {
          headersBuilder += directHyperbusToFacade(k) → v
        }
    }

    // todo: move this to HalFilter
    val newBodyContent = HalTransformer.transformEmbeddedObject(message.body.content, uriTransformer)

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

    (DynamicBody(newBodyContent), headersBuilder.result())
  }
}