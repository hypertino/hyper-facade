/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import java.util.regex.Pattern

import akka.actor.ActorSystem
import akka.event.Logging._
import akka.util.Timeout
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.metrics.loaders.MetricsReporterLoader
import com.hypertino.metrics.{MetricsTracker, ProcessMetrics}
import com.hypertino.service.config.ConfigExtenders._
import com.hypertino.service.control.api.Service
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import scaldi.{Injectable, Injector, TypeTagIdentifier}
import spray.can.server.ServerSettings
import spray.http._
import spray.routing._
import spray.routing.directives.LogEntry

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class RestServiceApp(implicit inj: Injector) extends SimpleRoutingApp
  with Service
  with Injectable
  with StrictLogging {

  implicit val timeout = Timeout(10 seconds)
  implicit val actorSystem = inject[ActorSystem]
  implicit val scheduler = inject[Scheduler]

  private val rootConf = inject[Config]

  val config = inject[Config]
  val restConfig = config.getConfig(FacadeConfigPaths.HTTP)
  val metricsTracker = inject[MetricsTracker]

  val shutdownTimeout = config.getFiniteDuration(FacadeConfigPaths.SHUTDOWN_TIMEOUT)

  val hyperBus = inject[Hyperbus] // it's time to initialize hyperbus

  val interface = restConfig.getString("host")
  val port = restConfig.getInt("port")

  def start(initRoutes: ⇒ Route) {
    import scala.reflect.runtime.universe._
    inj.getBinding(List(TypeTagIdentifier(typeOf[MetricsReporterLoader]))) match {
      case Some(_) ⇒
        inject[MetricsReporterLoader].run()
        ProcessMetrics.startReporting(metricsTracker)

      case None ⇒
        logger.warn("Metric reporter is not configured.")
    }

    startServer(interface, port, settings = Some(ServerSettings(rootConf))) {
      startWithDirectives(initRoutes)
    } onComplete {
      case Success(_) ⇒
        logger.info("HttpService successfully started.")

      case Failure(e) ⇒
        logger.error(s"Error on bind server to $interface:$port", e)
        sys.exit(1)
    }
  }

  def startWithDirectives(initRoutes: ⇒ Route): Route = {
    enableAccessLogIf(restConfig.getBoolean("access-log.enabled")) {
      addJsonMediaTypeIfNotExists() {
        respondWithCORSHeaders(restConfig.getStringList("cors.allowed-origins"), restConfig.getStringList("cors.allowed-paths").map(Pattern.compile)) {
          pathSuffix(Slash.?) {
            initRoutes
          }
        }
      }
    }
  }

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = {
    // todo: implement real stop
    Future.successful(logger.info("Hyperfacade is stopped"))

  }

  private def respondWithCORSHeaders(allowedOrigins: Seq[String], allowedPaths: Seq[Pattern] = Nil): Directive0 =
    optionalHeaderValueByName("Origin") flatMap {
      case Some(origin) ⇒
        val originUri = spray.http.Uri(origin)
        val originHost = originUri.authority.host.address
        if (allowedOrigins.isEmpty || allowedOrigins.exists(originHost.endsWith)) {
          requestInstance flatMap { request ⇒
            (if (request.method == HttpMethods.OPTIONS) {
              mapHttpResponse(resp ⇒ resp.copy(status = StatusCodes.OK, entity = HttpEntity.Empty, headers = resp.headers.filterNot(_.is("www-authenticate"))))
            } else Directive.Empty) &
              mapHttpResponseHeaders(headers ⇒
                headers ::: List(
                  HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(Seq(origin))),
                  HttpHeaders.`Access-Control-Expose-Headers`("Content-Length", headers.map(_.name): _*),
                  HttpHeaders.RawHeader("Access-Control-Allow-Methods", (Seq(request.method) ++ request.headers.find(_.lowercaseName == "access-control-request-method").map(_.value)).mkString(", ")),
                  HttpHeaders.RawHeader("Access-Control-Allow-Headers", request.headers.find(_.lowercaseName == "access-control-request-headers").map(_.value).getOrElse("Accept")),
                  HttpHeaders.`Access-Control-Allow-Credentials`(allow = true),
                  HttpHeaders.`Access-Control-Max-Age`(86400)
                )
              )
          }
        } else if (allowedPaths.nonEmpty) {
          requestUri flatMap { uri ⇒
            if (allowedPaths.exists(_.matcher(uri.path.toString()).matches())) {
              respondWithCORSHeaders(Nil)
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
        } else {
          reject(AuthorizationFailedRejection)
        }

      case _ ⇒ noop
    }

  private def addJsonMediaTypeIfNotExists(): Directive0 =
    mapHttpResponseEntity(_.flatMap {
      case ent if ent.contentType == ContentTypes.`text/plain(UTF-8)` ⇒
        ent.copy(contentType = ContentTypes.`application/json`)

      case ent ⇒ ent
    })

  private def enableAccessLogIf(enabled: Boolean): Directive0 =
    if (enabled) logRequestResponse(accessLogger _)
    else noop

  private def accessLogger(request: HttpRequest): HttpResponsePart ⇒ Option[LogEntry] = {
    val startTime = System.currentTimeMillis

    {
      case resp: HttpResponse ⇒
        Some(LogEntry(
          s"${request.method} ${request.uri} ${request.entity.asString} <--- ${resp.status} "
            + (if (resp.entity.toOption.exists(_.contentType.mediaType.isImage)) resp.entity.toOption.map(_.contentType) else resp.entity.asString.take(1000))
            + s" ${System.currentTimeMillis - startTime} ms",
          if (resp.status.isSuccess || resp.status.intValue == 404) InfoLevel else WarningLevel
        ))

      case _ ⇒ None
    }
  }
}

trait RestRoutes {

  implicit val defaultTimeout = Timeout(10 seconds)

  def routes: Route
}
