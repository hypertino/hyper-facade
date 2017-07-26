package com.hypertino.facade.filter.http

import java.net.MalformedURLException

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.FunctionUtils.chain
import com.hypertino.facade.utils.HrlTransformer._
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.hrl.PlainQueryConverter
import com.hypertino.hyperbus.serialization.JsonContentTypeConverter
import com.hypertino.hyperbus.transport.api.matchers.Specific
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import monix.execution.Scheduler
import scaldi.Injector

import scala.concurrent.{ExecutionContext, Future}

class HttpWsRequestFilter(config: Config, ramlConfig: RamlConfiguration,
                          protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      try {
        val request = contextWithRequest.request
        //val rootPathPrefix = config.getString(FacadeConfigPaths.RAML_ROOT_PATH_PREFIX)
        // val uriTransformer = chain(removeRootPathPrefix(ramlConfig.baseUri, _: HRL), rewriteLinkForward(_: HRL, rewriteCountLimit, ramlConfig))

        val uri = spray.http.Uri(request.headers.hrl.location)
        val hrl = HRL(uri.path.toString, request.headers.hrl.query)

        val headersBuilder = Headers.builder
        var messageIdFound = false

        contextWithRequest.originalHeaders.foreach { kv ⇒
          (kv._1.toLowerCase, kv._2) match {
            case (FacadeHeaders.CONTENT_TYPE, value) ⇒
              JsonContentTypeConverter.universalJsonContentTypeToSimple(value) match {
                case Text(contentType) ⇒ headersBuilder.withContentType(Some(contentType))
                case Null ⇒ // ...
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

        contextWithRequest.copy(
          request = DynamicRequest(request.body, headersBuilder.requestHeaders())
        )
      } catch {
        case e: MalformedURLException ⇒
          implicit val mcx = contextWithRequest.request
          throw NotFound(ErrorBody("not-found", Some(e.getMessage)))
      }
    }
  }
}
