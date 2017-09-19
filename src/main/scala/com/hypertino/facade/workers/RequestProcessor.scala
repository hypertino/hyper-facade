package com.hypertino.facade.workers

import akka.pattern.AskTimeoutException
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.FutureUtils
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import com.hypertino.metrics.MetricsTracker
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.util.control.NonFatal

trait RequestProcessor extends Injectable with StrictLogging {
  implicit def injector: Injector
  implicit def scheduler: Scheduler
  val hyperbus = inject[Hyperbus]
  val ramlConfig = inject[RamlConfiguration]
  val beforeFilterChain = inject[FilterChain]("beforeFilterChain")
  val ramlFilterChain = inject[FilterChain]("ramlFilterChain")
  val afterFilterChain = inject[FilterChain]("afterFilterChain")
  val config = inject[Config]
  val metrics = inject[MetricsTracker]
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  def processRequestToFacade(cwr: RequestContext): Future[DynamicResponse] = {
    try {
      metrics.timeOfFuture(MetricKeys.REQUEST_PROCESS_TIME) {
        beforeFilterChain.filterRequest(cwr) flatMap { unpreparedContextWithRequest ⇒
          val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest)
          processRequestWithRaml(cwrBeforeRaml) flatMap { cwrRaml ⇒
            hyperbus.ask(cwrRaml.request).runAsync recover {
              handleHyperbusExceptions(cwrRaml)
            } flatMap { case response: DynamicResponse ⇒
              FutureUtils.chain(response, cwrRaml.stages.map { _ ⇒
                ramlFilterChain.filterResponse(cwrRaml, _: DynamicResponse)
              }) flatMap { r ⇒
                afterFilterChain.filterResponse(cwrRaml, r)
              }
            }
          }
        } recover handleFilterExceptions(cwr) { response ⇒
          response
        }
      }
    } catch {
      case NonFatal(ex) ⇒
        Future.failed(ex)
    }
  }

  def processRequestWithRaml(cwr: RequestContext): Future[RequestContext] = {
    if (cwr.stages.size > rewriteCountLimit) {
      Future.failed(
        new RewriteLimitReachedException(cwr.stages.size, rewriteCountLimit)
      )
    }
    else {
      ramlFilterChain.filterRequest(cwr) flatMap { filteredCWR ⇒
        val filteredRequest = filteredCWR.request
        if (filteredRequest.headers.hrl.location == cwr.request.headers.hrl.location) {
          Future.successful(filteredCWR)
        } else {
          logger.trace(s"Request is restarted from ${cwr.request} to $filteredRequest")
          val templatedRequest = withRamlResource(filteredRequest)
          val cwrNext = cwr.withNextStage(templatedRequest)
          processRequestWithRaml(cwrNext)
        }
      }
    }
  }

  def prepareContextAndRequestBeforeRaml(cwr: RequestContext) = {
    val facadeRequestWithRamlUri = withRamlResource(cwr.request)
    cwr.withNextStage(facadeRequestWithRamlUri, ramlEntryHeaders=Some(facadeRequestWithRamlUri.headers))
  }

  def withRamlResource(implicit request: DynamicRequest): DynamicRequest = {
    val hrl = ramlConfig.resourceHRL(request.headers.hrl, request.headers.method).getOrElse {
      val h = request.headers.hrl
      if (h.scheme.isEmpty) {
        // didn't rewrite into hb://
        // which means that resource wasn't found
        throw NotFound(ErrorBody("resource-not-found", Some(s"${h.location} is not found")))
      }
      h
    }

    val newHeaders = new HeadersBuilder()
      .++=(request.headers)
      .withHRL(hrl)
      .result()
    request.copy(headers = RequestHeaders(newHeaders))
  }

  def handleHyperbusExceptions(cwr: RequestContext) : PartialFunction[Throwable, DynamicResponse] = {
    case hyperbusError: ErrorResponseBase @unchecked ⇒
      logger.debug("Hyperbus error", hyperbusError)
      hyperbusError

    case e: NoTransportRouteException ⇒
      implicit val mcf = cwr.request
      val errorId = SeqGenerator.create()
      logger.error(s"Service not found #$errorId while handling ${cwr.originalHeaders.hrl.location}(${cwr.request.headers.hrl.location})", e)
      BadGateway(ErrorBody("service-not-found", Some(s"'Service for ${cwr.originalHeaders.hrl.location}' is not found.")))

    case _: AskTimeoutException ⇒
      implicit val mcf = cwr.request
      val errorId = SeqGenerator.create()
      logger.error(s"Timeout #$errorId while handling ${cwr.originalHeaders.hrl.location}(${cwr.request.headers.hrl.location})")
      GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${cwr.originalHeaders.hrl.location}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, cwr)
  }

  def handleFilterExceptions[T](cwr: RequestContext)(func: DynamicResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        logger.error(s"Request execution interrupted: ${cwr.originalHeaders.hrl.location}", e)
      }
      else {
        logger.trace(s"Request execution interrupted: ${cwr.originalHeaders.hrl.location}", e)
      }
      func(e.response)

    case NonFatal(e) ⇒
      func(handleHyperbusExceptions(cwr)(e))
  }

  def handleInternalError(exception: Throwable, cwr: RequestContext): DynamicResponse = {
    implicit val mcf = cwr.request
    val errorId = IdGenerator.create()
    logger.error(s"Exception #$errorId while handling ${cwr.originalHeaders.hrl.location}(${cwr.request.headers.hrl.location})", exception)
    InternalServerError(ErrorBody("internal-server-error", Some(exception.getClass.getName + ": " + exception.getMessage), errorId = errorId))
  }
}
