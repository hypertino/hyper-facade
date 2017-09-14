package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.raml.{FetchAnnotation, RamlAnnotation, RamlConfiguration, RamlFieldAnnotation}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, EmptyBody, HRL, Header, HyperbusError, MessagingContext, Method, NotFound, Ok}
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FetchFieldFilter(protected val annotation: FetchAnnotation,
                       protected val hyperbus: Hyperbus,
                       protected val expressionEvaluator: ExpressionEvaluator,
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
    val ctx = ExpressionEvaluatorContext(context.requestContext, context.extraContext)
    try {
      val location = expressionEvaluator.evaluate(ctx, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(ctx, kv._2)
      }
      val hrl = HRL(location, query)
      //val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(location), Method.GET).getOrElse(hrlOriginal)

      implicit val mcx = context.requestContext
      ask(hrl, ctx).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(context.fieldPath.mkString("."), ctx, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(context.fieldPath.mkString("."), ctx, e)
    }
  }
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