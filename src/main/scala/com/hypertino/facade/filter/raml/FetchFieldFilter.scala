package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml.{FetchAnnotation, RamlAnnotation, RamlConfiguration}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, EmptyBody, HRL, Method, NotFound, Ok}
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.util.control.NonFatal

class FetchFieldFilter(annotation: FetchAnnotation,
                       hyperbus: Hyperbus,
                       expressionEvaluator: ExpressionEvaluator,
                       protected implicit val injector: Injector,
                       protected implicit val scheduler: Scheduler) extends FieldFilter with Injectable {

  protected val log = LoggerFactory.getLogger(getClass)

  protected lazy val ramlConfiguration = inject[RamlConfiguration]
  protected final val ON_ERROR_FAIL = "fail"
  protected final val ON_ERROR_REMOVE = "remove"
  protected final val ON_ERROR_DEFAULT = "default"

  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    try {
      val source = expressionEvaluator.evaluate(context.requestContext, context.extraContext, annotation.source).toString
      val hrlOriginal = HRL.fromURL(source)
      val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(source), Method.GET).getOrElse(hrlOriginal)

      hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
        case Ok(body: DynamicBody) ⇒
          Some(body.content)
      } onErrorRecoverWith {
        case NotFound(_) ⇒
          defaultValue(context)

        case NonFatal(e) ⇒
          handleError(context, e)
      }
    } catch {
      case NonFatal(e) ⇒
        handleError(context, e)
    }
  }

  protected def handleError(context: FieldFilterContext, e: Throwable): Task[Option[Value]] = {
    if (log.isDebugEnabled) {
      log.debug(s"Can't fetch ${context.path}", e)
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

  def defaultValue(context: FieldFilterContext): Task[Option[Value]] = Task.now {
    annotation.defaultValue.map { defV ⇒
      Some(expressionEvaluator.evaluate(context.requestContext, context.extraContext, defV))
    } getOrElse {
      Some(Null)
    }
  }
}

class FetchFieldFilterFactory(hyperbus: Hyperbus,
                              protected val predicateEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler) extends RamlFieldFilterFactory {
  def createFieldFilter(typeName: String, fieldName: String, annotation: RamlAnnotation): FieldFilter = {
    new FetchFieldFilter(annotation.asInstanceOf[FetchAnnotation], hyperbus, predicateEvaluator, injector, scheduler)
  }
}
