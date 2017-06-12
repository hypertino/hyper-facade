package com.hypertino.facade.filter.http

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value._
import com.hypertino.facade.filter.model.{EventFilter, ResponseFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HalTransformer
import com.hypertino.facade.utils.UriTransformer._
import com.hypertino.hyperbus.model.Links._
import com.hypertino.hyperbus.model.{DefLink, Header}
import com.hypertino.hyperbus.transport.api.uri.Uri
import spray.http.HttpHeaders

import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config) extends ResponseFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: ContextWithRequest, response: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = chain(rewriteLinkToOriginal(_: Uri, rewriteCountLimit), addRootPathPrefix(rootPathPrefix))
      val (newHeaders, newBody) = HttpWsFilter.filterMessage(response, uriTransformer)
      response.copy(
        headers = newHeaders,
        body = newBody
      )
    }
  }
}

class WsEventFilter(config: Config) extends EventFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)
  override def apply(contextWithRequest: ContextWithRequest, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
      val uriTransformer = chain(rewriteLinkToOriginal(_: Uri, rewriteCountLimit), addRootPathPrefix(rootPathPrefix))
      val (newHeaders, newBody) = HttpWsFilter.filterMessage(request, uriTransformer)
      request.copy(
        uri = addRootPathPrefix(rootPathPrefix)(Uri(request.uri.formatted)),
        headers = newHeaders,
        body = newBody
      )
    }
  }
}

object HttpWsFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap

  def filterMessage(message: FacadeMessage, uriTransformer: (Uri ⇒ Uri)): (Map[String, Seq[String]], Value) = {
    val headersBuilder = Map.newBuilder[String, Seq[String]]
    message.headers.foreach {
      case (Header.CONTENT_TYPE, value :: _) ⇒
        headersBuilder += FacadeHeaders.CONTENT_TYPE →
          FacadeHeaders.genericContentTypeToHttp(Some(value)).toSeq

      case (k, v) ⇒
        if (directHyperbusToFacade.contains(k)) {
          headersBuilder += directHyperbusToFacade(k) → v
        }
    }

    val newBody = HalTransformer.transformEmbeddedObject(message.body, uriTransformer)
    if (newBody.isInstanceOf[Obj] /* && response.status == 201*/ ) {
      // Created, set header value
      newBody.__links.fromValue[Option[LinksMap]].flatMap(_.get(DefLink.LOCATION)) match {
        case Some(Left(l)) ⇒
          val newHref = Uri(l.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case Some(Right(la)) ⇒
          val newHref = Uri(la.head.href).pattern.specific
          headersBuilder += (HttpHeaders.Location.name → Seq(newHref))
        case _ ⇒
      }
    }

    (headersBuilder.result(), newBody)
  }
}