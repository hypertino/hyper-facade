package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.raml.FetchAnnotationBase
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, EmptyBody, ErrorBody, HRL, Header, HyperbusError, InternalServerError, MessagingContext, Method, NotFound, Ok}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task

trait FetchFilterBase extends StrictLogging{
  protected def hyperbus: Hyperbus
  protected def annotation: FetchAnnotationBase
  protected def expressionEvaluator: ExpressionEvaluator

  protected def ask(hrl: HRL, context: ExpressionEvaluatorContext)(implicit mcx: MessagingContext): Task[Option[Value]] = {
    annotation.expects match {
      case "collection_link" ⇒
        val hrlCollectionLink = hrl.copy(query = hrl.query + Obj.from("per_page" → 0))
        hyperbus.ask(DynamicRequest(hrlCollectionLink, Method.GET, EmptyBody)).map {
          case response @ Ok(_: DynamicBody, _) ⇒
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
              applySelector(Obj(
                Map("top" → body.content) ++
                  response.headers.link.map(kv ⇒ kv._1 → Text(kv._2.toURL())) ++
                  response.headers.get(Header.COUNT).map("count" → _).toMap
              ), context)
            )
        }

      case "single_item" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            body.content match {
              case Lst(l) if l.size == 1 ⇒ Some(applySelector(l.head, context))
              case Lst(l) if l.isEmpty ⇒
                throw NotFound(ErrorBody("single-item-not-found", Some(s"$hrl")))
              case Lst(_) ⇒
                throw InternalServerError(ErrorBody("single-item-ambiguous", Some(s"$hrl")))
              case o: Obj ⇒
                throw InternalServerError(ErrorBody("resource-is-not-a-collection", Some(s"$hrl")))
            }
        }

      case "document" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case Ok(body: DynamicBody, _) ⇒
            Some(applySelector(body.content, context))
        }
    }
  }

  protected def handleError(s: String, context: ExpressionEvaluatorContext, e: Throwable): Task[Option[Value]] = {
    import FetchFieldFilter._
    if (e.isInstanceOf[NotFound[_]])
      logger.trace(s"$s is not found", e)
    else
      logger.debug(s"Can't fetch $s", e)
    if (annotation.onError == ON_ERROR_REMOVE) {
      Task.now(None)
    }
    else if (annotation.onError == ON_ERROR_DEFAULT) {
      defaultValue(context)
    }
    else {
      e match {
        case h: HyperbusError[_] if annotation.defaultStatuses.contains(h.headers.statusCode) ⇒
          defaultValue(context)
        case _ ⇒
          Task.raiseError(e)
      }
    }
  }

  def defaultValue(context: ExpressionEvaluatorContext): Task[Option[Value]] = Task.now {
    annotation.defaultValue.map { defV ⇒
      Some(expressionEvaluator.evaluate(context, defV))
    } getOrElse {
      Some(Null)
    }
  }

  protected def applySelector(v: Value, context: ExpressionEvaluatorContext): Value = {
    annotation.selector.map { expression ⇒
      val c = context.copy(extraContext = context.extraContext % Obj.from("source" → v))
      expressionEvaluator.evaluate(c, expression)
    } getOrElse {
      v
    }
  }
}
