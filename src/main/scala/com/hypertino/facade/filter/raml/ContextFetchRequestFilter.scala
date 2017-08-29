package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.ContextFetchAnnotation
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{HRL, NotFound}
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ContextFetchRequestFilter(protected val annotation: ContextFetchAnnotation,
                                protected val hyperbus: Hyperbus,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends RequestFilter with FetchFilterBase {

  protected val log = LoggerFactory.getLogger(getClass)

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
    try {
      val location = expressionEvaluator.evaluate(contextWithRequest, Null, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(contextWithRequest, Null, kv._2)
      }
      val hrl = HRL(location, query)

      implicit val mcx = contextWithRequest
      ask(hrl).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(hrl, contextWithRequest, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(HRL(annotation.location.source), contextWithRequest, e)
    }
  }

  protected def handleError(hrl: HRL, context: RequestContext, e: Throwable): Task[Option[Value]] = {
    import FetchFieldFilter._
    if (log.isDebugEnabled) {
      log.debug(s"Can't fetch $hrl", e)
    }
    if (annotation.onError == ON_ERROR_REMOVE) {
      Task.now(None)
    }
    else if (annotation.onError == ON_ERROR_DEFAULT) {
      defaultValue(context)
    }
    else {
      Task.raiseError(e)
    }
  }

  def defaultValue(context: RequestContext): Task[Option[Value]] = Task.now {
    annotation.defaultValue.map { defV ⇒
      Some(expressionEvaluator.evaluate(context, Null, defV))
    } getOrElse {
      Some(Null)
    }
  }


  override protected def expects: String = annotation.expects
}


