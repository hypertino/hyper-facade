/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import akka.pattern.AskTimeoutException
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.{MetricUtils, TaskUtils}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api.NoTransportRouteException
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import com.hypertino.metrics.MetricsTracker
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.util.control.NonFatal
import MetricUtils._

trait RequestProcessor extends Injectable with StrictLogging {
  implicit def injector: Injector

  implicit def scheduler: Scheduler

  val hyperbus = inject[Hyperbus]
  val ramlConfig = inject[RamlConfiguration]
  val beforeResolvedFilterChain = inject[FilterChain]("before_resolved")
  val afterResolvedFilterChain = inject[FilterChain]("after_resolved")
  val annotationsFilterChain = inject[FilterChain]("annotations")
  val afterReplyFilterChain = inject[FilterChain]("after_reply")
  val config = inject[Config]
  val metricsTracker = inject[MetricsTracker]
  val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  def processRequestToFacade(cwr: RequestContext): Task[DynamicResponse] = {
    try {
      MetricUtils.timeOfTask(MetricKeys.TOTAL_REQUEST_TIME, metricsTracker, {
        beforeResolvedFilterChain.filterRequest(cwr, metricsTracker) flatMap { unpreparedContextWithRequest ⇒
          prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest) flatMap { cwrBeforeRaml ⇒
            processRequestWithRaml(cwrBeforeRaml) flatMap { cwrRaml ⇒
              metricsTracker.timeOfTask(MetricKeys.specificRequest(cwrRaml.request.headers.hrl.location)) {
                hyperbus.ask(cwrRaml.request) onErrorRecover {
                  handleHyperbusExceptions(cwrRaml)
                } flatMap { response: DynamicResponse ⇒
                  TaskUtils.chain(response, cwrRaml.stages.map { _ ⇒
                    annotationsFilterChain.filterResponse(cwrRaml, _: DynamicResponse, metricsTracker)
                  }) flatMap { r ⇒
                    afterReplyFilterChain.filterResponse(cwrRaml, r, metricsTracker)
                  }
                }
              }
            }
          }
        } onErrorRecover handleFilterExceptions(cwr) { response ⇒
          response
        }
      })
    } catch {
      case NonFatal(ex) ⇒
        Task.raiseError(ex)
    }
  }

  def processRequestWithRaml(cwr: RequestContext): Task[RequestContext] = {
    if (cwr.stages.size > rewriteCountLimit) {
      Task.raiseError(
        new RewriteLimitReachedException(cwr.stages.size, rewriteCountLimit)
      )
    }
    else {
      annotationsFilterChain.filterRequest(cwr, metricsTracker) flatMap { filteredCWR ⇒
        val filteredRequest = filteredCWR.request
        if (filteredRequest.headers.hrl.location == cwr.request.headers.hrl.location) {
          Task.now(filteredCWR)
        } else {
          logger.trace(s"Request is restarted from ${cwr.request} to $filteredRequest")
          val templatedRequest = withRamlResource(filteredRequest)
          val cwrNext = cwr.withNextStage(templatedRequest)
          processRequestWithRaml(cwrNext)
        }
      }
    }
  }

  def prepareContextAndRequestBeforeRaml(cwr: RequestContext): Task[RequestContext] = {
    val facadeRequestWithRamlUri = withRamlResource(cwr.request)
    val resolvedRequest = cwr.withNextStage(facadeRequestWithRamlUri, ramlEntryHeaders = Some(facadeRequestWithRamlUri.headers))
    afterResolvedFilterChain.filterRequest(resolvedRequest, metricsTracker)
  }

  def withRamlResource(implicit request: DynamicRequest): DynamicRequest = {
    val hrl = ramlConfig.resourceHRL(request.headers.hrl, request.headers.method).getOrElse {
      val h = request.headers.hrl
      if (h.scheme.isEmpty) {
        // didn't rewrite into hb://
        // which means that resource wasn't found
        throw NotFound(ErrorBody(ErrorCode.NOT_FOUND, Some(s"Resource ${h.location} is not found")))
      }
      h
    }

    val newHeaders = new HeadersBuilder()
      .++=(request.headers)
      .withHRL(hrl)
      .result()
    request.copy(headers = RequestHeaders(newHeaders))
  }

  def handleHyperbusExceptions(cwr: RequestContext): PartialFunction[Throwable, DynamicResponse] = {
    case hyperbusError: ErrorResponseBase@unchecked ⇒
      logger.debug("Hyperbus error", hyperbusError)
      hyperbusError

    case e: NoTransportRouteException ⇒
      implicit val mcf = cwr.request
      val errorId = SeqGenerator.create()
      logger.error(s"Service not found #$errorId while handling ${cwr.httpHeaders.hrl.location}(${cwr.request.headers.hrl.location})", e)
      BadGateway(ErrorBody(ErrorCode.SERVICE_NOT_FOUND, Some(s"'Service for ${cwr.httpHeaders.hrl.location}' is not found.")))

    case _: AskTimeoutException ⇒
      implicit val mcf = cwr.request
      val errorId = SeqGenerator.create()
      logger.error(s"Timeout #$errorId while handling ${cwr.httpHeaders.hrl.location}(${cwr.request.headers.hrl.location})")
      GatewayTimeout(ErrorBody(ErrorCode.SERVICE_TIMEOUT, Some(s"Timeout while serving '${cwr.httpHeaders.hrl.location}'"), errorId = errorId))

    case NonFatal(nonFatal) ⇒
      handleInternalError(nonFatal, cwr)
  }

  def handleFilterExceptions[T](cwr: RequestContext)(func: DynamicResponse ⇒ T): PartialFunction[Throwable, T] = {
    case e: FilterInterruptException ⇒
      if (e.getCause != null) {
        logger.error(s"Request execution interrupted: ${cwr.httpHeaders.hrl.location}", e)
      }
      else {
        logger.trace(s"Request execution interrupted: ${cwr.httpHeaders.hrl.location}", e)
      }
      func(e.response)

    case NonFatal(e) ⇒
      func(handleHyperbusExceptions(cwr)(e))
  }

  def handleInternalError(exception: Throwable, cwr: RequestContext): DynamicResponse = {
    implicit val mcf = cwr.request
    val errorId = IdGenerator.create()
    logger.error(s"Exception #$errorId while handling ${cwr.httpHeaders.hrl.location}(${cwr.request.headers.hrl.location})", exception)
    InternalServerError(ErrorBody(ErrorCode.INTERNAL_SERVER_ERROR, Some(exception.getClass.getName + ": " + exception.getMessage), errorId = errorId))
  }
}
