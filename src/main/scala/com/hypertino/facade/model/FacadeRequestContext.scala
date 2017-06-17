package com.hypertino.facade.model

import com.hypertino.binders.value.Text
import com.hypertino.hyperbus.model.{DynamicRequest, MessagingContext, RequestHeaders}
import com.hypertino.hyperbus.util.IdGenerator

case class FacadeRequestContext(
                                 remoteAddress: String,
                                 httpUri: spray.http.Uri,
                                 //                                 pathAndQuery: String,
                                 //method: String,
                                 originalHeaders: RequestHeaders,
                                 preparedHeaders: Option[RequestHeaders],
                                 contextStorage: Map[String, Any]
                               ) {
  lazy val clientCorrelationId: Option[String] = {
    originalHeaders
      .get(FacadeHeaders.CLIENT_CORRELATION_ID)
      .orElse {
        originalHeaders
          .get(FacadeHeaders.CLIENT_MESSAGE_ID)
      } match {
      case Some(Text(s)) ⇒ Some(s)
      case other ⇒
        throw new RequestFormatException(s"Request doesn't contains correlation information for the reply: '$other'")
    }
  }

  def clientMessagingContext() = {
    MessagingContext(clientCorrelationId.getOrElse(IdGenerator.create()))
  }
}

/*  def prepare(requestHeaders: RequestHeaders) = copy(
    preparedHeaders = Some(requestHeaders)
  )
}*/

/*
object FacadeRequestContext {

  def create(remoteAddress: String, httpRequest: HttpRequest, parsedRequest: DynamicRequest) = {
    FacadeRequestContext(
      remoteAddress,
      httpRequest.uri,
      //parsedRequest.uri.pattern.specific,
      parsedRequest.method,
      // http headers always override request headers
      // this could be important for WS request
      parsedRequest.headers ++ normalizeHeaders(httpRequest.headers),
      None,
      Map.empty
    )
  }

  def normalizeHeaders(headers: List[HttpHeader]): Map[String, Seq[String]] = {
    headers.foldLeft(Map.newBuilder[String, Seq[String]]) { (facadeRequestHeaders, httpHeader) ⇒
      facadeRequestHeaders += (httpHeader.name → Seq(httpHeader.value))
    }.result()
  }

}
*/

/*// todo: better name?
case class RequestStage(
                         requestHRI: HRI,
                         requestMethod: String
                       )
*/

// todo: better name?
case class ContextWithRequest(context: FacadeRequestContext, request: DynamicRequest, stages: Seq[RequestHeaders]) {
  def withNextStage(nextRequest: DynamicRequest): ContextWithRequest = copy(
    context = context.copy(preparedHeaders = Some(nextRequest.headers)),
    stages = Seq(request.headers) ++ stages,
    request = nextRequest
  )
}
/*
object ContextWithRequest {
  def apply(context: FacadeRequestContext, request: FacadeRequest): ContextWithRequest = new ContextWithRequest(context, Seq.empty, request)
}
*/