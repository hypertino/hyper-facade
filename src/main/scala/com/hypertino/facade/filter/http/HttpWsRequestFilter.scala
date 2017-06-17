package com.hypertino.facade.filter.http

import java.net.MalformedURLException

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value.Null
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HalTransformer
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
        val uriTransformer = chain(removeRootPathPrefix(rootPathPrefix, _: HRL), rewriteLinkForward(_: HRL, rewriteCountLimit, ramlConfig))
        val hrl = removeRootPathPrefix(rootPathPrefix, request.headers.hrl)

        val headersBuilder = Headers.builder
        var messageIdFound = false

        contextWithRequest.context.originalHeaders.foreach {
          case (FacadeHeaders.CLIENT_CONTENT_TYPE, value) ⇒
            headersBuilder += Header.CONTENT_TYPE → JsonContentTypeConverter.universalJsonContentTypeToSimple(value)

          case (FacadeHeaders.CLIENT_MESSAGE_ID, value) if !value.isNull ⇒
            headersBuilder += Header.MESSAGE_ID → value
            messageIdFound = true

          case (k, v) ⇒
            if (HttpWsRequestFilter.directFacadeToHyperbus.contains(k)) {
              headersBuilder += HttpWsRequestFilter.directFacadeToHyperbus(k) → v
            }
        }

        if (!messageIdFound) {
          headersBuilder += Header.MESSAGE_ID → IdGenerator.create()
        }

        val transformedBodyContent = HalTransformer.transformEmbeddedObject(request.body.content, uriTransformer)

        contextWithRequest.copy(
          request = DynamicRequest(DynamicBody(transformedBodyContent), headersBuilder.requestHeaders())
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

object HttpWsRequestFilter {
  val directFacadeToHyperbus =  FacadeHeaders.clientHeaderMapping.toMap
}
