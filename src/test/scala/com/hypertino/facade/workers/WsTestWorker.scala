package com.hypertino.facade.workers

import akka.actor.ActorRef
import com.hypertino.facade.filter.chain.FilterChain
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.utils.MessageTransformer
import com.hypertino.hyperbus.model.DynamicRequest
import spray.can.websocket.frame.TextFrame
import spray.can.{Http, websocket}
import spray.routing.HttpServiceActor

abstract class WsTestWorker(filterChain: FilterChain) extends HttpServiceActor with websocket.WebSocketServerWorker {
  import context._
  private var _serverConnection: ActorRef = _

  def serverConnection = _serverConnection

  override def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      _serverConnection = sender()
      context.become(handshaking orElse closeLogic)
      serverConnection ! Http.Register(context.self)
  }

  def businessLogic: Receive = {
    case frame: TextFrame =>
      implicit val scheduler = monix.execution.Scheduler.Implicits.global
      val facadeRequest = MessageTransformer.frameToRequest(frame, "127.0.0.1", null) // todo: http request mock
      // val context = mockContext(facadeRequest)
      filterChain.filterRequest(RequestContext(facadeRequest)) map { filteredCWR ⇒
        exposeFacadeRequest(filteredCWR.request)
      }
  }

  def exposeFacadeRequest(facadeRequest: DynamicRequest): Unit
}