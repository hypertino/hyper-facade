/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import akka.actor.ActorSystem
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.utils.MessageTransformer
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.Injector
import spray.http._
import spray.routing.Directives._
import spray.routing._

class HttpWorker(implicit val injector: Injector) extends RequestProcessor {
  implicit val actorSystem = inject[ActorSystem]
  implicit val scheduler: Scheduler = inject[Scheduler]
  val trackHeartbeat = metricsTracker.meter(MetricKeys.HEARTBEAT)

  val restRoutes = new RestRoutes {
    val request = extract(_.request)
    val routes: Route =
      request { (request) ⇒
        clientIP { ip =>
          complete {
            processRequest(request, ip.toString) runAsync
          }
        }
      }
  }

  def processRequest(request: HttpRequest, remoteAddress: String): Task[HttpResponse] = {
    trackHeartbeat.mark()
    val (dynamicRequest, payloadOption) = MessageTransformer.httpToRequest(request, remoteAddress)
    //val contextStorage =
    processRequestToFacade(com.hypertino.facade.model.RequestContext(dynamicRequest, payloadOption)) map { response ⇒
      MessageTransformer.messageToHttpResponse(response)
    }
  }
}

