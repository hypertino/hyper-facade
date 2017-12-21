/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
  logger.info(s"${getClass.getName} is INITIALIZED")

  override def startService(): Unit = {
    app.start(httpWorker.restRoutes.routes)
    logger.info(s"${getClass.getName} is STARTED")
  }

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = {
    app.stop(timeout).map { _ ⇒
      logger.info(s"${getClass.getName} is STOPPED")
    }
  }
}
