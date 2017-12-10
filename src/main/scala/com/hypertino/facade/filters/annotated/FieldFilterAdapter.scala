/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.{Lst, Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, RamlConfiguration, TypeDefinition}
import com.hypertino.facade.utils.MetricUtils
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import com.hypertino.metrics.MetricsTracker
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

class RequestFieldFilterAdapter(val typeDef: TypeDefinition,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler,
                                protected implicit val metricsTracker: MetricsTracker) // todo: remove ec: ExecutionContext
  extends RequestFilter with FieldFilterBase with Injectable {

  val timer = Some(MetricKeys.specificFilter("RequestFieldFilterAdapter"))

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(requestContext: RequestContext)
           (implicit scheduler: Scheduler): Task[RequestContext] = {
    filterBody(requestContext.request.body.content, requestContext, FieldFilterStageRequest).map { body ⇒
      requestContext.copy(
        request = requestContext.request.copy(body = DynamicBody(body))
      )
    }
  }
}

class ResponseFieldFilterAdapter(val typeDef: TypeDefinition,
                                 protected val expressionEvaluator: ExpressionEvaluator,
                                 protected implicit val injector: Injector,
                                 protected implicit val scheduler: Scheduler,
                                 protected implicit val metricsTracker: MetricsTracker)
  extends ResponseFilter with FieldFilterBase with Injectable{

  val timer = Some(MetricKeys.specificFilter("ResponseFieldFilterAdapter"))

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(requestContext: RequestContext, response: DynamicResponse)
           (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    filterBody(response.body.content, requestContext, FieldFilterStageResponse).map { body ⇒
      StandardResponse(body = DynamicBody(body), response.headers)
        .asInstanceOf[DynamicResponse]
    }
  }
}


class EventFieldFilterAdapter(val typeDef: TypeDefinition,
                              protected val expressionEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler,
                              protected implicit val metricsTracker: MetricsTracker)
  extends EventFilter with FieldFilterBase with Injectable {

  val timer = Some(MetricKeys.specificFilter("RequestFieldFilterAdapter"))

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(requestContext: RequestContext, event: DynamicRequest)
           (implicit scheduler: Scheduler): Task[DynamicRequest] = {
    filterBody(event.body.content, requestContext, FieldFilterStageEvent).map { body ⇒
      DynamicRequest(DynamicBody(body), requestContext.request.headers)
    }
  }
}

class FieldFilterAdapterFactory(protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler,
                                protected implicit val metricsTracker: MetricsTracker) extends Injectable {
  def createFilters(typeDef: TypeDefinition): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq(new RequestFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler, metricsTracker)),
      responseFilters = Seq(new ResponseFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler, metricsTracker)),
      eventFilters = Seq(new EventFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler, metricsTracker))
    )
  }
}

trait FieldFilterBase {
  protected def typeDef: TypeDefinition
  protected implicit def scheduler: Scheduler
  protected def expressionEvaluator: ExpressionEvaluator
  protected def metricsTracker: MetricsTracker

  protected def filterBody(body: Value, requestContext: RequestContext, stage: FieldFilterStage): Task[Value] = {
    recursiveFilterValue(body, body, requestContext, typeDef, Seq.empty, stage)
  }

  protected def recursiveFilterValue(rootValue: Value,
                                     value: Value,
                                     requestContext: RequestContext,
                                     typeDef: TypeDefinition,
                                     fieldPath: Seq[String], stage: FieldFilterStage): Task[Value] = {
    if (typeDef.isCollection) {
      val tc = typeDef.copy(isCollection = false)
      Task.gather {
        value.toSeq.map { li ⇒
          recursiveFilterValue(rootValue, li, requestContext, tc, fieldPath, stage)
        }
      }.map(Lst(_))
    } else {
      val m = value.toMap
      val updateExistingFields = m.map { case (k, v) ⇒
        typeDef
          .fields
          .get(k)
          .map(filterMatching(_, rootValue, Some(v), value, fieldPath, requestContext, stage))
          .getOrElse {
            Task.now(k → Some(v))
          }
      }.toSeq

      val newFields = typeDef
        .fields
        .filterNot(f ⇒ m.contains(f._1))
        .map { case (_, field) ⇒
          filterMatching(field, rootValue, None, value, fieldPath, requestContext, stage)
        }
        .toSeq

      Task.gather(updateExistingFields ++ newFields).flatMap { res ⇒
        Task.gather {
          res.map {
            case (k, Some(v)) ⇒
              typeDef
                .fields
                .get(k)
                .flatMap { field ⇒
                  field
                    .typeDefinition
                    .map { innerTypeDef ⇒
                      recursiveFilterValue(rootValue, v, requestContext, innerTypeDef, fieldPath :+ field.fieldName, stage)
                        .map(vv ⇒ Some(k → vv))
                    }
                }.getOrElse {
                Task.now(Some(k → v))
              }

            case (_, None) ⇒
              Task.now(None)
          }
        } map { inner ⇒
          Obj.from(inner.flatten: _*)
        }
      }
    }
  }

  protected def filterMatching(field: Field,
                               rootValue: Value,
                               value: Option[Value],
                               siblings: Value,
                               parentFieldPath: Seq[String],
                               requestContext: RequestContext,
                               stage: FieldFilterStage): Task[(String, Option[Value])] = {
    val extraContext = Obj.from(
      "this" → siblings,
      "root" → rootValue,
      "stage" → stage.stringValue
    )
    val ctx = ExpressionEvaluatorContext(requestContext, extraContext)
    field
      .annotations
      .filter {
        fa ⇒
          fa.annotation.stages.contains(stage) &&
          fa.annotation.predicate.forall(expressionEvaluator.evaluatePredicate(ctx, _))
      }
      .foldLeft(Task.now(field.fieldName → value)) { case (lastValueTask, a) ⇒
        lastValueTask.flatMap { lastValue ⇒
          val timerName = MetricKeys.specificFieldFilter(typeDef.typeName + "-" + parentFieldPath.mkString("-") + field.fieldName)
          MetricUtils.timeOfTask(timerName, metricsTracker,
            a.filter.apply(FieldFilterContext(parentFieldPath :+ field.fieldName, lastValue._2, field, extraContext, requestContext, stage))
              .map(field.fieldName → _)
          )
        }
      }
  }
}




