/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.{DefaultValueSerializerFactory, Lst, Null, Obj, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext, PreparedExpression}
import com.hypertino.facade.model.{ErrorCode, FilterInterruptException}
import com.hypertino.facade.raml.{RamlConfigException, RamlConfiguration, RamlFieldAnnotation}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{BadRequest, ErrorBody, HRL, InternalServerError, MessagingContext}
import com.hypertino.inflector.naming.{CamelCaseToSnakeCaseConverter, PlainConverter, SnakeCaseToCamelCaseConverter}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class FetchFieldAnnotation(
                                 @fieldName("if") predicate: Option[PreparedExpression],
                                 location: PreparedExpression,
                                 query: Map[String, PreparedExpression],
                                 expects: String = FetchFilter.EXPECTS_DOCUMENT,   //todo: this should be enum
                                 onError: String = FetchFilter.ON_ERROR_DEFAULT,   //todo: this should be enum
                                 default: Map[String, PreparedExpression] = Map("401" -> PreparedExpression("null")),
                                 stages: Set[FieldFilterStage] = Set(FieldFilterStageResponse, FieldFilterStageEvent),
                                 selector: Option[PreparedExpression] = None,
                                 always: Boolean = false,
                                 iterateOn: Option[PreparedExpression] = None
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
          implicit val mcx = context.requestContext
          if (fieldsSelected(fields, context)) {
            fetchAndReturnField(context)
          }
          else {
            Task.now(None)
          }
      }
    }
  }

  protected def fieldsSelected(fields: Value, context: FieldFilterContext)
                              (implicit mcx: MessagingContext): Boolean = try {
    val selectFields = SelectFields(fields.toString)
    recursiveMatch(context.fieldPath, selectFields)
  } catch {
    case e: Throwable ⇒
      throw BadRequest(ErrorBody(ErrorCode.MALFORMED_FIELDS_FILTER, Some(e.toString)))
  }

  @tailrec private def recursiveMatch(fieldPath: Seq[String], selectFields: Map[String, SelectField]): Boolean = {
    if (fieldPath.nonEmpty) {
      val matchedFields = selectFields.get("**").orElse(selectFields.get(fieldPath.head))
      matchedFields match {
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
      annotation.iterateOn.map { iterateOnExpression =>
        val source = expressionEvaluator.evaluate(context.expressionEvaluatorContext, iterateOnExpression)
        source match {
          case Obj(v) =>
            Task.gather(v.map{ item =>
              val extraContext = context.expressionEvaluatorContext.extraContext % Obj.from(
                "key" -> item._1,
                "item" -> item._2
              )
              val expressionEvaluatorContext = context.expressionEvaluatorContext.copy(extraContext=extraContext)
              fetchSingle(context, expressionEvaluatorContext).map{
                case Some(newV) => Some(item._1 -> newV)
                case None => None
              }
            }).map { seq =>
              Some(Obj(seq.flatten.toMap))
            }

          case Lst(v) =>
            Task.gather(v.map{ item =>
              val extraContext = context.expressionEvaluatorContext.extraContext % Obj.from(
                "item" -> item
              )
              val expressionEvaluatorContext = context.expressionEvaluatorContext.copy(extraContext=extraContext)
              fetchSingle(context, expressionEvaluatorContext)
            }).map { seq =>
              Some(Lst(seq.flatten))
            }

          case Null =>
            Task.now(None)
          case _ =>
            implicit val mcx = context.requestContext
            throw InternalServerError(ErrorBody(ErrorCode.FIELD_IS_NOT_ITERABLE, Some(s"Field ${context.fieldPath.mkString(".")} value is not iterable")))
        }
      } getOrElse {
        fetchSingle(context, context.expressionEvaluatorContext)
      }
    } catch {
      case NonFatal(e) ⇒
        handleError(context.fieldPath.mkString("."), context.expressionEvaluatorContext, e)
    }
  }

  protected def fetchSingle(context: FieldFilterContext, expressionContext: ExpressionEvaluatorContext): Task[Option[Value]] = {
    val location = expressionEvaluator.evaluate(expressionContext, annotation.location).toString
    val query = annotation.query.map { kv ⇒
      kv._1 → expressionEvaluator.evaluate(expressionContext, kv._2)
    }
    val hrl = HRL(location, query)
    implicit val mcx = expressionContext.requestContext
    ask(hrl, expressionContext).onErrorRecoverWith {
      case NonFatal(e) ⇒
        handleError(context.fieldPath.mkString("."), expressionContext, e)
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
    value.to[FetchFieldAnnotation]
  }
}
