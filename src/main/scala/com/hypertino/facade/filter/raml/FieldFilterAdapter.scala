package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, FieldAnnotationWithFilter, RamlConfiguration, TypeDefinition}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class RequestFieldFilterAdapter(val typeDef: TypeDefinition,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler) // todo: remove ec: ExecutionContext
  extends RequestFilter with FieldFilterBase with Injectable {

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext)
           (implicit ec: ExecutionContext): Future[RequestContext] = {
    filterBody(contextWithRequest.request.body.content, contextWithRequest).map { body ⇒
      contextWithRequest.copy(
        request = contextWithRequest.request.copy(body = DynamicBody(body))
      )
    }.runAsync
  }

  override protected def typeDefinitions: Map[String, TypeDefinition] = ramlConfiguration.dataTypes
}

class ResponseFieldFilterAdapter(val typeDef: TypeDefinition,
                                 protected val expressionEvaluator: ExpressionEvaluator,
                                 protected implicit val injector: Injector,
                                 protected implicit val scheduler: Scheduler)
  extends ResponseFilter with FieldFilterBase with Injectable{

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext, response: DynamicResponse)
           (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    filterBody(response.body.content, contextWithRequest).map { body ⇒
      StandardResponse(body = DynamicBody(body), response.headers)
        .asInstanceOf[DynamicResponse]
    }.runAsync
  }

  override protected def typeDefinitions: Map[String, TypeDefinition] = ramlConfiguration.dataTypes
}


class EventFieldFilterAdapter(val typeDef: TypeDefinition,
                              protected val expressionEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler)
  extends EventFilter with FieldFilterBase with Injectable {

  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(contextWithRequest: RequestContext, event: DynamicRequest)
           (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    filterBody(event.body.content, contextWithRequest).map { body ⇒
      DynamicRequest(DynamicBody(body), contextWithRequest.request.headers)
    }.runAsync
  }

  override protected def typeDefinitions: Map[String, TypeDefinition] = ramlConfiguration.dataTypes
}

class FieldFilterAdapterFactory(protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val injector: Injector,
                                protected implicit val scheduler: Scheduler) extends Injectable {
  def createFilters(typeDef: TypeDefinition): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq(new RequestFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler)),
      responseFilters = Seq(new ResponseFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler)),
      eventFilters = Seq(new EventFieldFilterAdapter(typeDef, predicateEvaluator, injector, scheduler))
    )
  }
}

trait FieldFilterBase {
  protected def typeDef: TypeDefinition
  protected def typeDefinitions: Map[String, TypeDefinition]
  protected implicit def scheduler: Scheduler
  protected def expressionEvaluator: ExpressionEvaluator

  protected def filterBody(body: Value, requestContext: RequestContext): Task[Value] = {
    recursiveFilterValue(body, body, requestContext, typeDef, "")
  }

  protected def recursiveFilterValue(rootValue: Value,
                                     value: Value,
                                     requestContext: RequestContext,
                                     typeDef: TypeDefinition,
                                     fieldPath: String): Task[Value] = {
    if (typeDef.isCollection) {
      val tc = typeDef.copy(isCollection = false)
      Task.gather {
        value.toSeq.map { li ⇒
          recursiveFilterValue(rootValue, li, requestContext, tc, fieldPath + "[]")
        }
      }.map(Lst(_))
    } else {
      val m = value.toMap
      val updateExistingFields = m.map { case (k, v) ⇒
        typeDef
          .fields
          .get(k)
          .map(filterMatching(_, rootValue, Some(v), value, fieldPath, requestContext))
          .getOrElse {
            Task.now(k → Some(v))
          }
      }.toSeq

      val newFields = typeDef
        .fields
        .filterNot(f ⇒ m.contains(f._1))
        .map { case (_, field) ⇒
          filterMatching(field, rootValue, None, value, fieldPath, requestContext)
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
                  typeDefinitions
                    .get(field.typeName)
                    .map { innerTypeDef ⇒
                      recursiveFilterValue(rootValue, v, requestContext, innerTypeDef, fieldPath + "." + field.name)
                        .map(vv ⇒ Some(k → vv))
                    }
                }.getOrElse {
                Task.now(Some(k → v))
              }

            case (k, None) ⇒
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
                               parentFieldPath: String,
                               requestContext: RequestContext): Task[(String, Option[Value])] = {
    val extraContext = Obj.from(
      "this" → siblings,
      "root" → rootValue
    )
    field
      .annotations
      .find {
        _.annotation.predicate.forall(expressionEvaluator.evaluatePredicate(requestContext, extraContext, _))
      }
      .map { a ⇒
        a.filter(FieldFilterContext(parentFieldPath + "." + field.name, value, field, extraContext, requestContext)).map(field.name → _)
      }
      .getOrElse {
        Task.now(field.name → value)
      }
  }
}




