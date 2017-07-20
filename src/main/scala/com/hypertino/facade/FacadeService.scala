package com.hypertino.facade

import com.hypertino.facade.workers.{HttpWorker, WsRestServiceApp}
import com.hypertino.service.control.api.Service
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class FacadeService(implicit val injector: Injector) extends Service with Injectable {
  protected val log = LoggerFactory.getLogger(getClass.getName)
  protected val httpWorker = inject [HttpWorker]

  val app = new WsRestServiceApp
  app.start(httpWorker.restRoutes.routes)

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = {
    app.stopService(controlBreak, timeout)
  }
}
