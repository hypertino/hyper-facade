package com.hypertino.facade.filter.http

import com.hypertino.binders.value._
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.model.{EventFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.HrlTransformer
import com.hypertino.hyperbus.model.{DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, HRL, Header, HeaderHRL, Headers, HeadersMap, RequestHeaders, ResponseHeaders, StandardResponse}
import com.typesafe.config.Config

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config,
                           protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      //todo: implement rewriting back
      val (body, headersObj) = HttpWsFilter.filterMessage(response, hrl ⇒ hrl)
      StandardResponse(body, ResponseHeaders(headersObj)).asInstanceOf[DynamicResponse]
    }
  }
}

class WsEventFilter(config: Config, ramlConfig: RamlConfiguration,
                    protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)
  override def apply(contextWithRequest: RequestContext, request: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {
      val (newBody, newHeaders) = HttpWsFilter.filterMessage(request, hrl ⇒ hrl) // todo: root/baseUri
      val n = Headers
        .builder
        .++=(newHeaders)
        .withHRL(request.headers.hrl)
        .result()
      DynamicRequest(newBody, RequestHeaders(n))
    }
  }
}

object HttpWsFilter {
  private final val MAX_REWRITES=10

  def httpLink(link: Value): String = {
    link match {
      case o @ Obj(links) ⇒
        links.map {
          case(k, v: Obj) ⇒
            "<" + v.to[HRL].toURL().replace(",", "%2C") + ">; rel=" + k
          case(k, other) ⇒
            "<" + other.toString.replace(",", "%2C") + ">; rel=" + k
        } mkString ", "

      case other ⇒ other.toString
    }
  }

  def filterMessage(message: DynamicMessage, uriTransformer: (HRL ⇒ HRL)): (DynamicBody, HeadersMap) = {
    val headersBuilder = Headers.builder

    message.headers.foreach {
      // todo: transform?
      case (Header.LOCATION, v) ⇒
        FacadeHeaders.LOCATION → rewriteHrlToHttpUrl(v.to[HRL])

      case (Header.HRL, v) ⇒       // todo: events, remove scheme!!!
        FacadeHeaders.LOCATION → rewriteHrlToHttpUrl(v.to[HRL])

      case (Header.LINK, v) ⇒
        FacadeHeaders.LINK → httpLink(v)

      case (k, v) ⇒
        if (FacadeHeaders.directHeaderMapping.contains(k)) {
          headersBuilder += k → v
        }
    }

    val newBody = DynamicBody(filterBodyContent(message.body.content), message.body.contentType)
    (newBody, headersBuilder.result())
  }

  def filterBodyContent(c: Value): Value = {
    c match {
      case o: Obj ⇒
        Obj(
          o.v.map {
            case (k, v) if k.endsWith("_url") ⇒
              k → rewriteHrlToHttpUrl(v)

            case (k, v) ⇒
              k -> filterBodyContent(v)
          }
        )

      case l: Lst ⇒
        Lst(
          l.v.map(filterBodyContent)
        )

      case _ ⇒ c
    }
  }

  def rewriteHrlToHttpUrl(v: Value): Value = v match {
    case v: Obj if v.contains(HeaderHRL.LOCATION) ⇒
      Text(rewriteHrlToHttpUrl(v.to[HRL]))

    case v: Text ⇒
      Text(rewriteHrlToHttpUrl(HRL.fromURL(v.v)))

    case _ ⇒ v
  }

  def rewriteHrlToHttpUrl(hrl: HRL): String = {
    HrlTransformer.rewriteLinkToOriginal(hrl, MAX_REWRITES).toURL()
  }
}