package com.hypertino.facade.workers

import akka.pattern.AskTimeoutException
import com.hypertino.binders.value.Obj
import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{RamlConfigurationReader, RamlStrictConfigException}
import com.hypertino.facade.utils.FutureUtils
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.util.IdGenerator
import com.hypertino.metrics.MetricsTracker
import org.slf4j.Logger
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait RequestProcessor extends Injectable {
  def log: Logger
  implicit def injector: Injector
  implicit def executionContext: ExecutionContext
  val hyperbus = inject[Hyperbus]
  val ramlConfig = inject[RamlConfigurationReader]
  val beforeFilterChain = inject[FilterChain]("beforeFilterChain")
  val ramlFilterChain = inject[FilterChain]("ramlFilterChain")
  val afterFilterChain = inject[FilterChain]("afterFilterChain")
  val config = inject[Config]
  val metrics = inject[MetricsTracker]
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  def processRequestToFacade(cwr: ContextWithRequest): Future[DynamicResponse] = {
    metrics.timeOfFuture(MetricKeys.REQUEST_PROCESS_TIME) {
      beforeFilterChain.filterRequest(cwr) flatMap { unpreparedContextWithRequest ⇒
        val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest)
        processRequestWithRaml(cwrBeforeRaml) flatMap { cwrRaml ⇒
          hyperbus.ask(cwrRaml.request).runAsync recover {
            handleHyperbusExceptions(cwrRaml)
          } flatMap { response ⇒
            FutureUtils.chain(response, cwrRaml.stages.map { _ ⇒
              ramlFilterChain.filterResponse(cwrRaml, _: DynamicResponse)
            }) flatMap { r ⇒
              afterFilterChain.filterResponse(cwrRaml, r.asInstanceOf[DynamicResponse])
            }
          }
        }
      } recover handleFilterExceptions(cwr) { response ⇒
        response
      }
    }
  }

  def processRequestWithRaml(cwr: ContextWithRequest): Future[ContextWithRequest] = {
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
          if (log.isDebugEnabled) {
            log.debug(s"Request ${cwr.context} is restarted from ${cwr.request} to $filteredRequest")
          }
          val templatedRequest = withRamlResource(filteredRequest)
          val cwrNext = cwr.withNextStage(templatedRequest)
          processRequestWithRaml(cwrNext)
        }
      }
    }
  }

  def prepareContextAndRequestBeforeRaml(cwr: ContextWithRequest) = {
    val facadeRequestWithRamlUri = withRamlResource(cwr.request)
    cwr.withNextStage(facadeRequestWithRamlUri)
  }

  def withRamlResource(request: DynamicRequest): DynamicRequest = {
    val ramlHRL = ramlConfig.resourceHRL(request.headers.hrl, request.headers.method)
    val newHeaders = new HeadersBuilder()
      .++=(request.headers.all)
      .withHRL(ramlHRL)
      .result()
    request.copy(headers = RequestHeaders(newHeaders))
  }

  def handleHyperbusExceptions(cwr: ContextWithRequest) : PartialFunction[Throwable, Response[DynamicBody]] = {
    case hyperbusError: HyperbusError[ErrorBody] ⇒
      hyperbusError

    case _: NoTransportRouteException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      NotFound(ErrorBody("not-found", Some(s"'${cwr.context.originalHeaders.hrl.location}' is not found.")))

    case _: AskTimeoutException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.error(s"Timeout #$errorId while handling ${cwr.context}")
      GatewayTimeout(ErrorBody("service-timeout", Some(s"Timeout while serving '${cwr.context.originalHeaders.hrl.location}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, cwr)
  }

  def handleFilterExceptions[T](cwr: ContextWithRequest)(func: DynamicResponse ⇒ T) : PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        log.error(s"Request execution interrupted: ${cwr.context}", e)
      }
      else if (log.isDebugEnabled) {
        log.debug(s"Request execution interrupted: ${cwr.context}", e)
      }
      func(e.response)

    case e: RamlStrictConfigException ⇒
      implicit val mcf = cwr.context.clientMessagingContext()
      val errorId = IdGenerator.create()
      log.info(s"Exception #$errorId while handling ${cwr.context}", e)
      func(NotFound(ErrorBody("not-found", Some("Resource is not found"), errorId = errorId)))

    case NonFatal(nonFatal) ⇒
      val response = handleInternalError(nonFatal, cwr)
      func(response)
  }

  def handleInternalError(exception: Throwable, cwr: ContextWithRequest): Response[ErrorBody] = {
    implicit val mcf = cwr.context.clientMessagingContext()
    val errorId = IdGenerator.create()
    log.error(s"Exception #$errorId while handling ${cwr.context}", exception)
    InternalServerError(ErrorBody("internal-server-error", Some(exception.getClass.getName + ": " + exception.getMessage), errorId = errorId))
  }
}
