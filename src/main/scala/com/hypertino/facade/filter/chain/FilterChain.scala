package com.hypertino.facade.filter.chain

import com.hypertino.facade.filter.model.{EventFilter, RequestFilter, ResponseFilter}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.FutureUtils
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicResponse}

import scala.concurrent.{ExecutionContext, Future}

trait FilterChain {
  def filterRequest(contextWithRequest: RequestContext)
                   (implicit ec: ExecutionContext): Future[RequestContext] = {
    FutureUtils.chain(contextWithRequest, findRequestFilters(contextWithRequest).map(f ⇒ f.apply(_)))
  }

  def filterResponse(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    FutureUtils.chain(response, findResponseFilters(contextWithRequest, response).map(f ⇒ f.apply(contextWithRequest, _ : DynamicResponse)))
  }

  def filterEvent(contextWithRequest: RequestContext, event: DynamicRequest)
                 (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    FutureUtils.chain(event, findEventFilters(contextWithRequest, event).map(f ⇒ f.apply(contextWithRequest, _ : DynamicRequest)))
  }

  def findRequestFilters(contextWithRequest: RequestContext): Seq[RequestFilter]
  def findResponseFilters(context: RequestContext, response: DynamicResponse): Seq[ResponseFilter]
  def findEventFilters(context: RequestContext, event: DynamicRequest): Seq[EventFilter]
}

object FilterChain {
  val empty = SimpleFilterChain(Seq.empty,Seq.empty,Seq.empty)
}
