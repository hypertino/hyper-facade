package eu.inn.facade.filter.http

import eu.inn.facade.model._
import eu.inn.hyperbus.model.Header

import scala.concurrent.{ExecutionContext, Future}

class WsEventFilter extends EventFilter {
  override def apply(context: EventFilterContext, request: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {
    Future {
      val headersBuilder = Map.newBuilder[String, Seq[String]]
      request.headers.foreach {
        case (Header.CONTENT_TYPE, value :: tail) ⇒
          headersBuilder += Header.CONTENT_TYPE → Seq(
            FacadeHeaders.CERTAIN_CONTENT_TYPE_START + value + FacadeHeaders.CERTAIN_CONTENT_TYPE_END
          )

        case (k, v) ⇒
          if (WsEventFilter.directHyperbusToFacade.contains(k)) {
            headersBuilder += WsEventFilter.directHyperbusToFacade(k) → v
          }
      }

      request.copy(
        headers = headersBuilder.result()
      )
    }
  }
}

object WsEventFilter {
  val directHyperbusToFacade = FacadeHeaders.directHeaderMapping.map(kv ⇒ kv._2 → kv._1).toMap
}

