package com.hypertino.facade

import com.hypertino.facade.workers.{HttpWorker, WsRestServiceApp}
import com.hypertino.service.control.api.Service
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class FacadeService(implicit val injector: Injector) extends Service with Injectable with StrictLogging {
  protected val httpWorker = inject[HttpWorker]
  protected implicit val scheduler = inject[Scheduler]

  val app = new WsRestServiceApp
  app.start(httpWorker.restRoutes.routes)
  logger.info(s"${getClass.getName} started")

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = {
    app.stopService(controlBreak, timeout).map { _ ⇒
      logger.info(s"${getClass.getName} stopped")
    }
  }
}
