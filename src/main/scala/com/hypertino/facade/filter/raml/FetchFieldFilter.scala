package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml.{RamlAnnotation, RamlConfiguration, RamlFieldAnnotation}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.HRL
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class FetchFieldAnnotation(
                                 predicate: Option[PreparedExpression],
                                 location: PreparedExpression,
                                 query: Map[String, PreparedExpression],
                                 expects: String, //todo: this should be enum
                                 onError: String, //todo: this should be enum
                                 defaultStatuses: Set[Int],
                                 default: Option[PreparedExpression],
                                 stages: Set[FieldFilterStage],
                                 selector: Option[PreparedExpression],
                                 always: Boolean
                          ) extends RamlFieldAnnotation with FetchAnnotationBase {
  def name: String = "fetch"
}


class FetchFieldFilter(protected val annotation: FetchFieldAnnotation,
                       protected val hyperbus: Hyperbus,
                       protected val expressionEvaluator: ExpressionEvaluator,
                       protected implicit val injector: Injector,
                       protected implicit val scheduler: Scheduler) extends FieldFilter with FetchFilterBase with Injectable with StrictLogging{

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (annotation.always) {
      fetchAndReturnField(context)
    } else {
      context.requestContext.request.headers.hrl.query.dynamic.fields match {
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
      logger.error(s"Can't parse 'fields' parameter", e)
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
      val location = expressionEvaluator.evaluate(context.expressionEvaluatorContext, annotation.location).toString
      val query = annotation.query.map { kv ⇒
        kv._1 → expressionEvaluator.evaluate(context.expressionEvaluatorContext, kv._2)
      }
      val hrl = HRL(location, query)
      //val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(location), Method.GET).getOrElse(hrlOriginal)

      implicit val mcx = context.requestContext
      ask(hrl, context.expressionEvaluatorContext).
        onErrorRecoverWith {
          case NonFatal(e) ⇒
            handleError(context.fieldPath.mkString("."), context.expressionEvaluatorContext, e)
        }
    } catch {
      case NonFatal(e) ⇒
        handleError(context.fieldPath.mkString("."), context.expressionEvaluatorContext, e)
    }
  }
}

class FetchFieldFilterFactory(hyperbus: Hyperbus,
                              protected val predicateEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler) extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = {
    new FetchFieldFilter(annotation.asInstanceOf[FetchFieldAnnotation], hyperbus, predicateEvaluator, injector, scheduler)
  }

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    import com.hypertino.hyperbus.serialization.SerializationOptions._
    import FieldFilterStage._
    import PreparedExpression._
    value.to[FetchFieldAnnotation]
  }
}

object FetchFieldFilter {
  final val ON_ERROR_FAIL = "fail"
  final val ON_ERROR_REMOVE = "remove"
  final val ON_ERROR_DEFAULT = "default"
}