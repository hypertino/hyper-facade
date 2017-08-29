package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Lst
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.utils.RequestUtils
import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError, NotFound}

import scala.concurrent.{ExecutionContext, Future}

class Item0RequestFilter(protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    val request = contextWithRequest.request
    implicit val mcx = contextWithRequest.request
    request.body.content match {
      case Lst(items) ⇒
        if (items.isEmpty) Future.failed {
          NotFound(ErrorBody("collection-is-empty", Some(s"Resource ${contextWithRequest.request.headers.hrl} is an empty collection")))
        }
        else {
          if (items.size > 1) Future.failed {
            InternalServerError(ErrorBody("collection-have-more-than-1-items", Some(s"Resource ${contextWithRequest.request.headers.hrl} have ${items.size} items")))
          }
          else Future {
            contextWithRequest.copy(request = RequestUtils.copyWithNewBody(contextWithRequest.request, items.head))
          }
        }

      case _ ⇒
        Future.failed {
          InternalServerError(ErrorBody("resource-is-not-collection"))
        }
    }
  }
}


