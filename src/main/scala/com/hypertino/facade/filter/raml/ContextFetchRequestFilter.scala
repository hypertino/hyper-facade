package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model._
import com.hypertino.facade.raml.ContextFetchAnnotation
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.HRL
import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ContextFetchRequestFilter(protected val annotation: ContextFetchAnnotation,
                                protected val hyperbus: Hyperbus,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends RequestFilter with FetchFilterBase {

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {

    fetchAndReturn(contextWithRequest).map {
      case Some(v) ⇒
        contextWithRequest.copy(contextStorage = contextWithRequest.contextStorage + Obj.from(annotation.target → v))
      case None ⇒
        contextWithRequest.copy(contextStorage = contextWithRequest.contextStorage - Lst.from(annotation.target))
    }.runAsync
  }

  protected def fetchAndReturn(contextWithRequest: RequestContext): Task[Option[Value]] = {
    val ctx = ExpressionEvaluatorContext(contextWithRequest, Obj.empty)
    try {
      val location = expressionEvaluator.evaluate(ctx, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(ctx, kv._2)
      }
      val hrl = HRL(location, query)

      implicit val mcx = contextWithRequest
      ask(hrl, ctx).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(hrl.toString, ctx, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(annotation.location.source, ctx, e)
    }
  }
}

