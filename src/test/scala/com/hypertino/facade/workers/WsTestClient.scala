/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.workers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.hypertino.facade.utils.MessageTransformer
import com.hypertino.hyperbus.model.{DynamicMessage, DynamicRequest, DynamicResponse, StandardResponse}
import com.hypertino.hyperbus.serialization.MessageReader
import org.asynchttpclient.DefaultAsyncHttpClient
import org.scalatest.concurrent.ScalaFutures
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.Success
import scala.util.control.NonFatal

case object Connect
case object Disconnect

class WsTestClient(connect: Http.Connect, val upgradeRequest: HttpRequest)
                  (implicit actorSystem: ActorSystem) extends WebSocketClientWorker {

  override def receive = {
    case Connect ⇒
      context.become(handshaking orElse closeLogic)
      IO(UHttp) ! connect
  }

  def businessLogic: Receive = {
    case websocket.UpgradedToWebSocket ⇒
      onUpgrade()

    case frame: TextFrame ⇒
      onMessage(frame)

    case message: DynamicMessage @unchecked ⇒
      connection ! MessageTransformer.messageToFrame(message)

    case _: Http.ConnectionClosed | Http.Closed ⇒
      context.stop(self)

    case Disconnect ⇒
      connection ! Http.Close
  }

  def onMessage(frame: TextFrame): Unit = ()
  def onUpgrade(): Unit = ()
}

trait WsTestClientHelper extends ScalaFutures {
  implicit def patience: PatienceConfig

  def createWsClient(name: String, uri: String,
                       onMessageString: String ⇒ Unit, host: String = "localhost", port: Int = 54321)
                      (implicit actorSystem: ActorSystem): ActorRef = {

    val connect = Http.Connect(host, port)
    val onUpgradeGetReq = HttpRequest(HttpMethods.GET, uri, upgradeHeaders(host, port))
    val onClientUpgradePromise = Promise[Boolean]()
    val client = actorSystem.actorOf(Props(new WsTestClient(connect, onUpgradeGetReq) {
      val lock = new Object
      override def onMessage(frame: TextFrame): Unit = {
        onMessageString(frame.payload.utf8String)
      }
      override def onUpgrade(): Unit = {
        onClientUpgradePromise.complete(Success(true))
      }
    }), name)
    client ! Connect // init websocket connection
    try {
      onClientUpgradePromise.future.futureValue
      client
    }
    catch {
      case NonFatal(e) ⇒
        actorSystem.stop(client)
        throw e
    }
  }

  def upgradeHeaders(host: String, port: Int) = List(
    HttpHeaders.Host(host, port),
    HttpHeaders.Connection("Upgrade"),
    HttpHeaders.RawHeader("Upgrade", "websocket"),
    HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
    HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="),
    HttpHeaders.RawHeader("Sec-WebSocket-Extensions", "permessage-deflate")
  )
}

class TestQueue(implicit actorSystem: ActorSystem, timeout: Timeout) {
  import actorSystem.dispatcher

  val actorRef = actorSystem.actorOf(Props(new Actor {
    val q = mutable.Queue[String]()
    override def receive: Receive = {
      case s: String ⇒
        q += s

      case GetNext ⇒
        if (q.nonEmpty)
          sender ! q.dequeue
        else
          context.become(waiting(sender))
    }

    def waiting(waiter: ActorRef): Receive = {
      case s: String ⇒
        waiter ! s
        context.unbecome()
    }
  }))

  def next(): Future[String] = {
    (actorRef ? GetNext).asInstanceOf[Future[String]]
  }

  def nextResponse(): Future[DynamicResponse] = next() map { s ⇒
    MessageReader.fromString(s, StandardResponse.dynamicDeserializer)
  }

  def nextEvent(): Future[DynamicRequest] = next() map { s ⇒
    MessageReader.fromString(s, DynamicRequest.apply)
  }

  def put(s: String): Unit = {
    actorRef ! s
  }

  case object GetNext
}
