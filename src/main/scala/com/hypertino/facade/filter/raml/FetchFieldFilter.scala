package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml.{FetchAnnotation, RamlAnnotation, RamlConfiguration, RamlFieldAnnotation}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, EmptyBody, HRL, Header, MessagingContext, Method, NotFound, Ok}
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FetchFieldFilter(annotation: FetchAnnotation,
                       protected val hyperbus: Hyperbus,
                       expressionEvaluator: ExpressionEvaluator,
                       protected implicit val injector: Injector,
                       protected implicit val scheduler: Scheduler) extends FieldFilter with FetchFilterBase with Injectable {

  protected val log = LoggerFactory.getLogger(getClass)
  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (annotation.always) {
      fetchAndReturnField(context)
    } else {
      context.requestContext.request.headers.hrl.query.fields match {
        case Null ⇒
          Task.now(None)

        case fields: Value ⇒
          if (fieldsSelected(fields, context)) {
            fetchAndReturnField(context)
          }
          else {
            Task.now(None)
          }
      }
    }
  }

  protected def fieldsSelected(fields: Value, context: FieldFilterContext) = Try(SelectFields(fields.toString)) match {
    case Success(selectFields) ⇒
      recursiveMatch(context.fieldPath, selectFields)

    case Failure(e) ⇒
      log.error(s"Can't parse 'fields' parameter", e)
      false
  }

  @tailrec private def recursiveMatch(fieldPath: Seq[String], selectFields: Map[String, SelectField]): Boolean = {
    if (fieldPath.nonEmpty) {
      selectFields.get(fieldPath.head) match {
        case Some(f) ⇒ recursiveMatch(fieldPath.tail, f.children)
        case None ⇒ false
      }
    }
    else {
      true
    }
  }

  protected def fetchAndReturnField(context: FieldFilterContext): Task[Option[Value]] = {
    try {
      val location = expressionEvaluator.evaluate(context.requestContext, context.extraContext, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(context.requestContext, context.extraContext, kv._2)
      }
      val hrl = HRL(location, query)
      //val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(location), Method.GET).getOrElse(hrlOriginal)

      implicit val mcx = context.requestContext
      ask(hrl).
        onErrorRecoverWith {
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
    import FetchFieldFilter._
    if (log.isDebugEnabled) {
      log.debug(s"Can't fetch ${context.fieldPath.mkString(".")}", e)
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

  override protected def expects: String = annotation.expects
}

class FetchFieldFilterFactory(hyperbus: Hyperbus,
                              protected val predicateEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler) extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = {
    new FetchFieldFilter(annotation.asInstanceOf[FetchAnnotation], hyperbus, predicateEvaluator, injector, scheduler)
  }
}

object FetchFieldFilter {
  final val ON_ERROR_FAIL = "fail"
  final val ON_ERROR_REMOVE = "remove"
  final val ON_ERROR_DEFAULT = "default"
}