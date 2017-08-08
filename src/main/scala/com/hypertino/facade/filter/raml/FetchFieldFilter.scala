package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.raml.{FetchAnnotation, RamlAnnotation, RamlConfiguration}
import com.hypertino.facade.utils.{SelectField, SelectFields}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, EmptyBody, HRL, Header, Method, NotFound, Ok}
import monix.eval.Task
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.annotation.tailrec
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class FetchFieldFilter(annotation: FetchAnnotation,
                       hyperbus: Hyperbus,
                       expressionEvaluator: ExpressionEvaluator,
                       protected implicit val injector: Injector,
                       protected implicit val scheduler: Scheduler) extends FieldFilter with Injectable {

  protected val log = LoggerFactory.getLogger(getClass)
  protected lazy val ramlConfiguration = inject[RamlConfiguration]

  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    context.requestContext.request.headers.hrl.query.fields match {
      case Null ⇒
        Task.now(None)

      case fields: Value ⇒
        if(fieldsSelected(fields, context)) {
          fetchAndReturnField(context)
        }
        else {
          Task.now(None)
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
      val source = expressionEvaluator.evaluate(context.requestContext, context.extraContext, annotation.source).toString
      val hrlOriginal = HRL.fromURL(source)
      val hrl = ramlConfiguration.resourceHRL(HRL.fromURL(source), Method.GET).getOrElse(hrlOriginal)

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

  protected def ask(hrl: HRL): Task[Option[Value]] = {
    annotation.mode match {
      case "collection_link" ⇒
        val hrlCollectionLink = hrl.copy(query = hrl.query + Obj.from("per_page" → 0))
        hyperbus.ask(DynamicRequest(hrlCollectionLink, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            Some(Obj(Map(
                "first_page_url" → Text(hrl.toURL())
            ) ++
              response.headers.get(Header.COUNT).map("count" → _)
            ))
        }

      case "collection_top" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            Some(
              Obj(
                Map("top" → body.content) ++
                nextPageUrl(hrl, body.content).map("next_page_url" → Text(_)).toMap ++
                response.headers.get(Header.COUNT).map("count" → _).toMap
              )
            )
        }

      case "document" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case Ok(body: DynamicBody, _) ⇒
            Some(body.content)
        }
    }
  }

  def nextPageUrl(hrl: HRL, content: Value): Option[String] = content match {
      !-!-
    case Lst(v) ⇒ v.lastOption.map(last ⇒ hrl.copy(query=hrl.query + Obj.from("filter" → s"id > '${last.id}'")).toURL())
    case _ ⇒ None
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
}

class FetchFieldFilterFactory(hyperbus: Hyperbus,
                              protected val predicateEvaluator: ExpressionEvaluator,
                              protected implicit val injector: Injector,
                              protected implicit val scheduler: Scheduler) extends RamlFieldFilterFactory {
  def createFieldFilter(typeName: String, fieldName: String, annotation: RamlAnnotation): FieldFilter = {
    new FetchFieldFilter(annotation.asInstanceOf[FetchAnnotation], hyperbus, predicateEvaluator, injector, scheduler)
  }
}

object FetchFieldFilter {
  final val ON_ERROR_FAIL = "fail"
  final val ON_ERROR_REMOVE = "remove"
  final val ON_ERROR_DEFAULT = "default"
}