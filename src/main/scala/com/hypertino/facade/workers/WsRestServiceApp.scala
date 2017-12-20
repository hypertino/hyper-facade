/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import akka.actor._
import akka.io.{IO, Inet, Tcp}
import akka.pattern.ask
import akka.util.Timeout
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.events.SubscriptionsManager
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.hyperbus.Hyperbus
import monix.execution.atomic.AtomicLong
import scaldi.{Injectable, Injector}
import spray.can.Http
import spray.can.Http.Unbind
import spray.can.server.{ServerSettings, UHttp}
import spray.io.ServerSSLEngineProvider
import spray.routing._

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}

class WsRestServiceApp(implicit inj: Injector)
  extends RestServiceApp
    with Injectable {

  private val activeConnections = AtomicLong(0)
  private val trackActiveConnections = metricsTracker.counter(MetricKeys.ACTIVE_CONNECTIONS)
  private val rejectedConnectionsMeter = metricsTracker.meter(MetricKeys.REJECTED_CONNECTS)

  val hyperbus = inject[Hyperbus]
  val subscriptionsManager = inject[SubscriptionsManager]
  private var serviceActorOption: Option[ActorRef] = None
  private var ioActorOption: Option[ActorRef] = None

  override def startServer(interface: String,
                           port: Int,
                           serviceActorName: String,
                           backlog: Int,
                           options: immutable.Traversable[Inet.SocketOption],
                           settings: Option[ServerSettings])(route: ⇒ Route)(implicit system: ActorSystem, sslEngineProvider: ServerSSLEngineProvider,
                                                                             bindingTimeout: Timeout): Future[Http.Bound] = {

    val maxConnectionCount = config.getInt(s"${FacadeConfigPaths.HTTP}.max-connections")

    val serviceActor = system.actorOf(
      props = Props {
        new Actor {
          val noMoreConnectionsWorker = context.actorOf(
            Props(classOf[NoMoreConnectionsWorker], maxConnectionCount),
            "no-more-connections"
          )
          var connectionId: Long = 0
          var connectionCount: Long = 0

          def receive = {
            case Http.Connected(remoteAddress, localAddress) =>
              if (connectionCount >= maxConnectionCount) {
                sender() ! Http.Register(noMoreConnectionsWorker)
                rejectedConnectionsMeter.mark()
              }
              else {
                val serverConnection = sender()
                connectionId += 1
                connectionCount += 1
                val worker = context.actorOf(
                  WsRestWorker.props(serverConnection,
                    new WsRestRoutes(route),
                    hyperbus,
                    subscriptionsManager,
                    remoteAddress.getAddress.toString.substring(1) // remove leading slash symbol
                  ), "wrkr-" + connectionId.toHexString
                )
                context.watch(worker)
                trackActiveConnections.inc()
                activeConnections.increment()
              }
            case Terminated(worker) ⇒
              connectionCount -= 1
              trackActiveConnections.dec()
              activeConnections.decrement()
          }
        }
      },
      name = serviceActorName)

    serviceActorOption = Some(serviceActor)

    val io = IO(UHttp)(system)
    ioActorOption = Some(io)
    io.ask(Http.Bind(serviceActor, interface, port, backlog, options, settings))(bindingTimeout).flatMap {
      case b: Http.Bound ⇒ Future.successful(b)
      case Tcp.CommandFailed(b: Http.Bind) ⇒
        // TODO: replace by actual exception when Akka #3861 is fixed.
        //       see https://github.com/akka/akka/issues/13861
        Future.failed(new RuntimeException(
          "Binding failed. Switch on DEBUG-level logging for `akka.io.TcpListener` to log the cause."))
    }(system.dispatcher)
  }

  def stop(timeout: FiniteDuration): Future[Unit] = Future {
    val until = timeout.toMillis + System.currentTimeMillis()
    ioActorOption.foreach(a ⇒ a ! Unbind)
    ioActorOption = None
    serviceActorOption.foreach { a ⇒
      val c = activeConnections.get
      while (c > 0 && until > System.currentTimeMillis()) {
        Thread.sleep(500)
        logger.info(s"Waiting for $c connections to finish...")
      }
      a ! PoisonPill
    }
  }
}
