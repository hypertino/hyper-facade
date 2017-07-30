package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, TypeDefinition}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, StandardResponse}
import monix.eval.Task
import monix.execution.Scheduler
import scaldi.{Injectable, Injector}

import scala.concurrent.{ExecutionContext, Future}

class RequestFieldFilterAdapter(val fields: Seq[FieldWithFilter],
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
}

class ResponseFieldFilterAdapter(protected val fields: Seq[FieldWithFilter],
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
}


class EventFieldFilterAdapter(protected val fields: Seq[FieldWithFilter],
                              protected val expressionEvaluator: ExpressionEvaluator,
                              protected implicit val scheduler: Scheduler)
  extends EventFilter with FieldFilterBase {

  def apply(contextWithRequest: RequestContext, event: DynamicRequest)
           (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    filterBody(event.body.content, contextWithRequest).map { body ⇒
      DynamicRequest(DynamicBody(body), contextWithRequest.request.headers)
    }.runAsync
  }
}

class FieldFilterAdapterFactory(protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler,
                                protected implicit val injector: Injector) extends Injectable {
  def createFilters(typeDef: TypeDefinition): SimpleFilterChain = {
    val fieldWithFilter = typeDef.fields.flatMap { field ⇒
      field.annotations.map { annotation ⇒
        val filterName = annotation.name + "Field"
        val filter = inject[RamlFieldFilterFactory](filterName).createFieldFilter(typeDef.typeName, annotation, field)
        FieldWithFilter(field, filter)
      }
    }

    SimpleFilterChain(
      requestFilters = Seq(new RequestFieldFilterAdapter(fieldWithFilter, predicateEvaluator, scheduler)),
      responseFilters = Seq(new ResponseFieldFilterAdapter(fieldWithFilter, predicateEvaluator, scheduler)),
      eventFilters = Seq(new EventFieldFilterAdapter(fieldWithFilter, predicateEvaluator, scheduler))
    )
  }
}

case class FieldWithFilter(field: Field, filter: FieldFilter)

case class SegmentFieldsTree(fields: Map[String, FieldWithFilter], inner: Map[String, SegmentFieldsTree])

object SegmentFieldsTree {
  def apply(fields: Seq[FieldWithFilter]): SegmentFieldsTree = {
    recursive(
      fields.map { field ⇒
        (field.field.identifier.segments, field)
      }.sorted(ordering)
    )
  }

  private def recursive(f: Seq[(Seq[String], FieldWithFilter)]): SegmentFieldsTree = {
    f.map { case (segments, field) ⇒
      if (segments.tail.isEmpty) {
        SegmentFieldsTree(Map(segments.head → field), Map.empty)
      }
      else {
        SegmentFieldsTree(Map.empty, Map(
          segments.head → recursive(Seq((segments.tail, field)))
        ))
      }
    }.foldLeft(SegmentFieldsTree(Map.empty, Map.empty)) { (result, current) ⇒
      SegmentFieldsTree(result.fields ++ current.fields, appendMaps(result.inner, current.inner))
    }
  }

  private def appendMaps(a: Map[String, SegmentFieldsTree], b: Map[String, SegmentFieldsTree]): Map[String, SegmentFieldsTree] = {
    a.map { case (key, value) ⇒
      b.get(key) match {
        case Some(i) ⇒ key → SegmentFieldsTree(value.fields ++ i.fields, appendMaps(value.inner, i.inner))
        case None ⇒ key → value
      }
    } ++ b.flatMap {
      case (key, value) ⇒
        a.get(key) match {
          case Some(_) ⇒ None
          case None ⇒ Some(key → value)
        }
    }
  }

  private val ordering: Ordering[(Seq[String], FieldWithFilter)] = new Ordering[(Seq[String], FieldWithFilter)] {
    override def compare(x: (Seq[String], FieldWithFilter), y: (Seq[String], FieldWithFilter)): Int = {
      (x._1.headOption, y._1.headOption) match {
        case (Some(xs), Some(ys)) ⇒
          val r = xs.compareTo(ys)
          if (r == 0) {
            compare((x._1.tail, x._2), (y._1.tail, y._2))
          } else {
            r
          }
        case (Some(_), None) ⇒ 1
        case (None, Some(_)) ⇒ -1
        case (None, None) ⇒ 0
      }
    }
  }
}

trait FieldFilterBase {
  protected def fields: Seq[FieldWithFilter]
  protected val fieldsTree = SegmentFieldsTree(fields)
  protected implicit def scheduler: Scheduler

  //def applyToField(field: Field, annotation: R, value: Value, requestContext: RequestContext): Option[Value]

  protected def filterBody(body: Value, requestContext: RequestContext): Task[Value] = {
    recursiveFilterValue(body, body, requestContext, fieldsTree)
  }

  private def recursiveFilterValue(rootValue: Value, value: Value, requestContext: RequestContext, fieldsTree: SegmentFieldsTree): Task[Value] = {
    val m = value.toMap
    Task.gather {
      m.map { case (key, v) ⇒
        {
          fieldsTree.fields.get(key) match {
            case Some(ff) ⇒ ff.filter(rootValue, ff.field, Some(v), requestContext)
            case None ⇒ Task.now(Some(v))
          }
        } flatMap {
          case Some(vv) ⇒
            fieldsTree.inner.get(key) match {
              case Some(innerTree) ⇒
                recursiveFilterValue(rootValue, vv, requestContext, innerTree).map(vvv ⇒ Some(key → vvv))

              case None ⇒ Task.now(Some(key → vv))
            }

          case None ⇒
            Task.now(None: Option[(String, Value)])
        }
      } ++ fieldsTree.fields.filterNot(f ⇒ m.contains(f._1)).map { case (key, ff) ⇒
        ff.filter(rootValue, ff.field, None, requestContext).map(vvv ⇒ vvv.map(key → _))
      } ++ fieldsTree.inner.filterNot(f ⇒ m.contains(f._1)).map { case (key, innerTree) ⇒
        noneTree(rootValue, requestContext, innerTree).map(vvv ⇒ vvv.map(key → _))
      }

    } map { i ⇒
      Obj.from(i.flatten.toSeq: _*)
    }
  }

  private def noneTree(rootValue: Value, requestContext: RequestContext, tree: SegmentFieldsTree): Task[Option[Value]] = {
    Task.gather {
      tree.fields.map { case (key, ff) ⇒
        ff.filter(rootValue, ff.field, None, requestContext).map(vvv ⇒ vvv.map(key → _))
      } ++
        tree.inner.map { case (key, innerTree) ⇒
          noneTree(rootValue, requestContext, innerTree).map(vvv ⇒ vvv.map(key → _))
        }
    } map { innerResult ⇒
      val seq = innerResult.flatten.toSeq
      if (seq.isEmpty) {
        None
      }
      else {
        Some(Obj.from(seq:_*))
      }
    }
  }
}




