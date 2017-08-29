package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Lst
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, ErrorBody, InternalServerError, NotFound, StandardResponse}

import scala.concurrent.{ExecutionContext, Future}

class ExtractItemResponseFilter(protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {

  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)(implicit ec: ExecutionContext): Future[DynamicResponse] = {
    implicit val mcx = contextWithRequest.request
    response.body.content match {
      case Lst(items) ⇒
        if (items.isEmpty) Future.failed {
          NotFound(ErrorBody("collection-is-empty", Some(s"Resource ${contextWithRequest.request.headers.hrl} is an empty collection")))
        }
        else {
          if (items.size > 1) Future.failed {
            InternalServerError(ErrorBody("collection-have-more-than-1-items", Some(s"Resource ${contextWithRequest.request.headers.hrl} have ${items.size} items")))
          }
          else Future {
            StandardResponse(DynamicBody(items.head), response.headers).asInstanceOf[DynamicResponse]
          }
        }

      case _ ⇒
        Future.failed {
          InternalServerError(ErrorBody("resource-is-not-collection"))
        }
    }
  }
}


