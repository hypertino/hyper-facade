package com.hypertino.facade.workers

import akka.actor._
import com.hypertino.facade.events.{FeedSubscriptionActor, SubscriptionsManager}
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model._
import com.hypertino.facade.utils.MessageTransformer
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicMessage, DynamicRequest, Ok}
import com.hypertino.metrics.MetricsTracker
import com.typesafe.scalalogging.StrictLogging
import scaldi.{Injectable, Injector}
import spray.can.server.UHttp
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.Frame
import spray.can.{Http, websocket}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import scala.util.control.NonFatal

class WsRestWorker(val serverConnection: ActorRef,
                   workerRoutes: WsRestRoutes,
                   hyperbus: Hyperbus,
                   subscriptionManager: SubscriptionsManager,
                   clientAddress: String)
                  (implicit inj: Injector)
  extends HttpServiceActor
  with websocket.WebSocketServerWorker
  with StrictLogging
  with Injectable {

  val metricsTrcker = inject[MetricsTracker]
  val trackWsTimeToLive = metricsTrcker.timer(MetricKeys.WS_LIFE_TIME).time()
  val trackWsMessages = metricsTrcker.meter(MetricKeys.WS_MESSAGE_COUNT)
  val trackHeartbeat = metricsTrcker.meter(MetricKeys.HEARTBEAT)
  var isConnectionTerminated = false
  var remoteAddress = clientAddress
  var httpRequest: Option[HttpRequest] = None

  override def preStart(): Unit = {
    super.preStart()
    serverConnection ! Http.Register(context.self)
    context.watch(serverConnection)
    if (log.isDebugEnabled) {
      log.debug(s"New connection with $serverConnection/$remoteAddress")
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    trackWsTimeToLive.stop()
  }
  // order is really important, watchConnection should be before httpRequests, otherwise there is a memory leak
  override def receive = watchConnection orElse businessLogic orElse httpRequests

  def watchConnection: Receive = {
    case handshakeRequest@websocket.HandshakeRequest(state) ⇒
      state match {
        case wsContext: websocket.HandshakeContext ⇒
          httpRequest = Some(wsContext.request)

          // todo: support Forwarded & by RFC 7239
          remoteAddress = wsContext.request.headers.find(_.is("X-Forwarded-For")).map(_.value).getOrElse(clientAddress)
        case _ ⇒
      }
      handshaking(handshakeRequest)

    case Terminated(`serverConnection`) ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is terminated")
      }
      context.stop(context.self)
      isConnectionTerminated = true

    case _: Http.ConnectionClosed ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Connection with $serverConnection/$remoteAddress is closing")
      }
      context.stop(serverConnection)

    case UHttp.Upgraded ⇒
      self ! websocket.UpgradedToWebSocket
  }

  def businessLogic: Receive = {
    case message: Frame ⇒
      try {
        trackWsMessages.mark()
        trackHeartbeat.mark()
        httpRequest match {
          case Some(h) ⇒
            val request = MessageTransformer.frameToRequest(message, remoteAddress, h)
            if (isPingRequest(request)) {
              pong(request)
            }
            else {
              processRequest(request)
            }

          case None ⇒
            throw new RuntimeException(s"httpRequest is empty while processing frame: $message")
        }
      }
      catch {
        case NonFatal(t) ⇒
          // todo: send error response to the client
          val msg = message.payload.utf8String
          //          val msgShort = msg.substring(0, Math.min(msg.length, 240))
          log.warning(s"Can't deserialize WS message '$msg' from ${sender()}/$remoteAddress. $t")
          None
      }

    case x: FrameCommandFailed =>
      log.error(s"Frame command $x failed from ${sender()}/$remoteAddress")

    case message: DynamicMessage @unchecked ⇒
      send(message)
  }

  def httpRequests: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      workerRoutes.route
    }
  }

  def processRequest(request: DynamicRequest): Unit = {
    val actorName = "Subscr-" + request.correlationId
    val contextWithRequest = RequestContext(request)
    context.child(actorName) match {
      case Some(actor) ⇒ actor.forward(contextWithRequest)
      case None ⇒ context.actorOf(FeedSubscriptionActor.props(self, hyperbus, subscriptionManager), actorName) ! contextWithRequest
    }
  }

  def isPingRequest(request: DynamicRequest): Boolean = {
    request.headers.hrl.location == "/status" && request.headers.method == "ping"
  }

  def pong(implicit request: DynamicRequest): Unit = {
    val response = Ok(DynamicBody("pong"))
    send(response)
  }

  def send(message: DynamicMessage): Unit = {
    if (isConnectionTerminated) {
      log.warning(s"Can't send message $message to $serverConnection/$remoteAddress: connection was terminated")
    }
    else {
      try {
        send(MessageTransformer.messageToFrame(message))
      } catch {
        case t: Throwable ⇒
          log.error(s"Can't serialize $message to $serverConnection/$remoteAddress", t)
      }
    }
  }
}

object WsRestWorker {
  def props(serverConnection: ActorRef,
            workerRoutes: WsRestRoutes,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager,
            clientAddress: String)
           (implicit inj: Injector) = Props(new WsRestWorker(
    serverConnection,
    workerRoutes,
    hyperbus,
    subscriptionManager,
    clientAddress))
}
