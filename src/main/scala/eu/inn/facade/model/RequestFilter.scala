package eu.inn.facade.model

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

case class RequestFilterContext(
                                 uri: Uri,
                                 httpUri: spray.http.Uri,
                                 method: String,
                                 headers: Map[String, Seq[String]],
                                 body: Value
                               )

trait RequestFilter extends Filter {
  def apply(context: RequestFilterContext, request: FacadeRequest)
           (implicit ec: ExecutionContext): Future[FacadeRequest]
}
