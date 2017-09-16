package com.hypertino.facade.workers

import akka.actor.ActorSystem
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.utils.MessageTransformer
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import scaldi.Injector
import spray.http._
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.Future

class HttpWorker(implicit val injector: Injector) extends RequestProcessor {
  implicit val actorSystem = inject[ActorSystem]
  implicit val scheduler = inject[Scheduler]
  val trackHeartbeat = metrics.meter(MetricKeys.HEARTBEAT)

  val restRoutes = new RestRoutes {
    val request = extract(_.request)
    val routes: Route =
      request { (request) ⇒
        clientIP { ip =>
          complete {
            processRequest(request, ip.toString)
          }
        }
      }
  }

  def processRequest(request: HttpRequest, remoteAddress: String): Future[HttpResponse] = {
    trackHeartbeat.mark()
    val dynamicRequest = MessageTransformer.httpToRequest(request, remoteAddress)
    processRequestToFacade(com.hypertino.facade.model.RequestContext(dynamicRequest)) map { response ⇒
      MessageTransformer.messageToHttpResponse(response)
    }
  }
}

