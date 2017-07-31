package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, RamlConfiguration, TypeDefinition}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class RequestFieldFilterAdapter(val typeDef: TypeDefinition,
                                protected val ramlConfiguration: RamlConfiguration,
                                protected val expressionEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) // todo: remove ec: ExecutionContext
  extends RequestFilter with FieldFilterBase {

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
                                 protected val ramlConfiguration: RamlConfiguration,
                                 protected val expressionEvaluator: ExpressionEvaluator,
                                 protected implicit val scheduler: Scheduler)
  extends ResponseFilter with FieldFilterBase {

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
                              protected val ramlConfiguration: RamlConfiguration,
                              protected val expressionEvaluator: ExpressionEvaluator,
                              protected implicit val scheduler: Scheduler)
  extends EventFilter with FieldFilterBase {

  def apply(contextWithRequest: RequestContext, event: DynamicRequest)
           (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    filterBody(event.body.content, contextWithRequest).map { body ⇒
      DynamicRequest(DynamicBody(body), contextWithRequest.request.headers)
    }.runAsync
  }

  override protected def typeDefinitions: Map[String, TypeDefinition] = ramlConfiguration.dataTypes
}

class FieldFilterAdapterFactory(protected val predicateEvaluator: ExpressionEvaluator,
                                protected val ramlConfiguration: RamlConfiguration,
                                protected implicit val scheduler: Scheduler,
                                protected implicit val injector: Injector) extends Injectable {
  def createFilters(typeDef: TypeDefinition): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq(new RequestFieldFilterAdapter(typeDef, ramlConfiguration, predicateEvaluator, scheduler)),
      responseFilters = Seq(new ResponseFieldFilterAdapter(typeDef, ramlConfiguration, predicateEvaluator, scheduler)),
      eventFilters = Seq(new EventFieldFilterAdapter(typeDef, ramlConfiguration, predicateEvaluator, scheduler))
    )
  }
}

trait FieldFilterBase {
  protected def typeDef: TypeDefinition
  protected def typeDefinitions: Map[String, TypeDefinition]
  protected implicit def scheduler: Scheduler

  protected def filterBody(body: Value, requestContext: RequestContext): Task[Value] = {
    recursiveFilterValue(body, body, requestContext, typeDef)
  }

  private def recursiveFilterValue(rootValue: Value,
                                   value: Value,
                                   requestContext: RequestContext,
                                   typeDef: TypeDefinition): Task[Value] = {
    if (typeDef.isCollection) {
      val tc = typeDef.copy(isCollection = false)
      Task.gather {
        value.toList.map { li ⇒
          recursiveFilterValue(rootValue, li, requestContext, tc)
        }
      }.map(Lst(_))
    } else {
      val m = value.toMap
      val updateExistingFields = m.flatMap { case (k, v) ⇒
        typeDef
          .fields
          .get(k)
          .map { field ⇒
            field.annotations.map { annotation ⇒
              annotation.filter(rootValue, field, Some(v), requestContext).map(k → _)
            }
          }
      }.toSeq.flatten

      val newFields = typeDef
        .fields
        .filterNot(f ⇒ m.contains(f._1))
        .flatMap { case (k, field) ⇒
          field.annotations.map { annotation ⇒
            annotation.filter(rootValue, field, None, requestContext).map(k → _)
          }
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
                    .get(field.name)
                    .map { innerTypeDef ⇒
                      recursiveFilterValue(rootValue, v, requestContext, innerTypeDef)
                        .map(vv ⇒ Some(k → v))
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
}




