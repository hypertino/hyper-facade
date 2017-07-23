package com.hypertino.facade.model

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{DynamicRequest, RequestHeaders}

case class RequestContext(request: DynamicRequest,
                          stages: Seq[RequestHeaders],
                          contextStorage: Obj) {

  lazy val originalHeaders: RequestHeaders = stages.reverse.head
  lazy val remoteAddress: String = originalHeaders(FacadeHeaders.REMOTE_ADDRESS).toString

  def withNextStage(newRequest: DynamicRequest): RequestContext = copy(
    stages = Seq(request.headers) ++ stages,
    request = newRequest
  )
}

object RequestContext {
  def apply(request: DynamicRequest): RequestContext = RequestContext(
    request,
    stages=Seq.empty,
    contextStorage=Obj.empty
  ).withNextStage(request)
}
