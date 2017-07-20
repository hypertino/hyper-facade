package com.hypertino.facade

import com.hypertino.facade.modules.Injectors
import com.hypertino.facade.workers.{HttpWorker, WsRestServiceApp}
import com.hypertino.service.control.api.Service
import scaldi.Injectable

// todo: reconsider MainApp, why we need this?
object MainApp extends App with Injectable {

  implicit val injector = Injectors()
  val httpWorker = inject [HttpWorker]

  inject[Service].asInstanceOf[WsRestServiceApp].start {
    httpWorker.restRoutes.routes
  }
}
