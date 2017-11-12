/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import akka.actor.ActorRefFactory
import com.typesafe.scalalogging.StrictLogging
import spray.http.ContentTypes._
import spray.http.{HttpEntity, HttpResponse}
import spray.routing._
import spray.routing.directives.{HeaderDirectives, RouteDirectives}

class NoMoreConnectionsWorker(maxConnectionCount: Int) extends HttpServiceActor with StrictLogging {
  def receive: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      HeaderDirectives.optionalHeaderValueByName("X-Forwarded-For") { forwardedFor ⇒
        RouteDirectives.complete {
          logger.warn(s"Maximum ($maxConnectionCount) active input connection count is exceed for ${forwardedFor.getOrElse("'address is unknown'")}.")
          HttpResponse(503, HttpEntity(`text/plain`, "Connection/worker limit is exceeded"))
        }
      }
    }
  }
}
