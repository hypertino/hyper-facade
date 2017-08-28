package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.Null
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.{HrlTransformer, RequestUtils}
import com.hypertino.hyperbus.model.HRL

import scala.concurrent.{ExecutionContext, Future}

class ForwardRequestFilter(sourceHRL: HRL,
                           locationExpression: PreparedExpression,
                           queryExpressionMap: Map[String, PreparedExpression],
                           protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val request = contextWithRequest.request

      val location = expressionEvaluator.evaluate(contextWithRequest, Null, locationExpression).toString
      val query = queryExpressionMap.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(contextWithRequest, Null, kv._2)
      }
      val destinationHRL = HRL(location, query)

      // todo: should we preserve all query fields???
      val rewrittenUri = HrlTransformer.rewriteForwardWithPatterns(request.headers.hrl, sourceHRL, destinationHRL)
      contextWithRequest.copy(
        request = RequestUtils.copyWithNewHRL(request, rewrittenUri)
      )
    }
  }
}


