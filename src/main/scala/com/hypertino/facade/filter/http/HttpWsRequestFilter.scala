package com.hypertino.facade.filter.http

import java.net.MalformedURLException

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HrlTransformer._
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.hrl.PlainQueryConverter
import com.hypertino.hyperbus.serialization.JsonContentTypeConverter
import com.hypertino.hyperbus.transport.api.matchers.Specific
import com.hypertino.hyperbus.util.IdGenerator

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter(config: Config, ramlConfig: RamlConfiguration) extends RequestFilter {
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: ContextWithRequest)
                    (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
    Future {
      try {
        val request = contextWithRequest.request
        val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
        // val uriTransformer = chain(removeRootPathPrefix(rootPathPrefix, _: HRL), rewriteLinkForward(_: HRL, rewriteCountLimit, ramlConfig))
        val hrl = removeRootPathPrefix(rootPathPrefix, request.headers.hrl)

        val headersBuilder = Headers.builder
        var messageIdFound = false

        contextWithRequest.originalHeaders.foreach {
          case (FacadeHeaders.CONTENT_TYPE, value) ⇒
            JsonContentTypeConverter.universalJsonContentTypeToSimple(value) match {
              case Text(contentType) ⇒ headersBuilder.withContentType(Some(contentType))
            }

          case (Header.MESSAGE_ID, value) if !value.isEmpty ⇒
            headersBuilder.withMessageId(value.toString)
            messageIdFound = true

          case (k, v) ⇒
            if (FacadeHeaders.directHeaderMapping.contains(k)) {
              headersBuilder += k → v
            }
        }

        if (!messageIdFound) {
          headersBuilder += Header.MESSAGE_ID → IdGenerator.create()
        }

        headersBuilder.withHRL(hrl)

        //todo: HAL ?
        //val transformedBodyContent = HalTransformer.transformEmbeddedObject(request.body.content, uriTransformer)

        contextWithRequest.copy(
          request = DynamicRequest(request.body, headersBuilder.requestHeaders())
        )
      } catch {
        case e: MalformedURLException ⇒
          implicit val mcx = contextWithRequest.request
          val error = NotFound(ErrorBody("not-found"))
          throw new FilterInterruptException(
            error,
            message = e.getMessage
          )
      }
    }
  }
}
