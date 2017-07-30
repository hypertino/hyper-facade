package com.hypertino.facade.model

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{DynamicRequest, HeadersMap, MessagingContext, RequestHeaders}

case class RequestContext(request: DynamicRequest,
                          stages: Seq[RequestHeaders],
                          contextStorage: Obj,
                          ramlEntryHeaders: Option[RequestHeaders] = None) extends MessagingContext {

  // todo: original is http?
  lazy val originalHeaders: RequestHeaders = RequestHeaders(HeadersMap(stages.reverse.head.toSeq.map(kv ⇒ kv._1.toLowerCase → kv._2): _*))
  lazy val remoteAddress: String = originalHeaders(FacadeHeaders.REMOTE_ADDRESS).toString

  def withNextStage(newRequest: DynamicRequest, ramlEntryHeaders: Option[RequestHeaders] = None): RequestContext = copy(
    stages = Seq(request.headers) ++ stages,
    request = newRequest,
    ramlEntryHeaders=this.ramlEntryHeaders.orElse(ramlEntryHeaders)
  )

  override def correlationId: String = request.correlationId
}

object RequestContext {
  def apply(request: DynamicRequest): RequestContext = RequestContext(
    request,
    stages=Seq.empty,
    contextStorage=Obj.empty
  ).withNextStage(request)
}
