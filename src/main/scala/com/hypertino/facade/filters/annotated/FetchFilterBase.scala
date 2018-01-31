/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model.ErrorCode
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, EmptyBody, ErrorBody, HRL, Header, Headers, HyperbusError, InternalServerError, MessagingContext, Method, NotFound, Ok, ResponseHeaders}
import com.hypertino.langutils.{LanguageRanges, ValueI18N}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task

trait FetchFilterBase extends StrictLogging{
  protected def hyperbus: Hyperbus
  protected def config: Config
  protected def annotation: FetchAnnotationBase
  protected def expressionEvaluator: ExpressionEvaluator

  // todo: move this to separate class shared with I18NResponseFilter
  private val defaultLocale = config.getString("hyperfacade.i18n-filter.default-locale")
  private val postfix = config.getString("hyperfacade.i18n-filter.fields-postfix")

  protected def ask(hrl: HRL, context: ExpressionEvaluatorContext)(implicit mcx: MessagingContext): Task[Option[Value]] = {
    annotation.expects match {
      case FetchFilter.EXPECTS_COLLECTION_LINK ⇒
        val hrlCollectionLink = hrl.copy(query = hrl.query + Obj.from("per_page" → 0))
        fetchResourceWithHeaders(hrlCollectionLink, context).map { case (_, headers) =>
          Some(Obj(Map(
            "first_page_url" → Text(hrl.toURL())
          ) ++
            headers.get(Header.COUNT).map("count" → _)
          ))
        }

      case FetchFilter.EXPECTS_COLLECTION_TOP ⇒
        fetchResourceWithHeaders(hrl, context).map {
          case (Lst(l), _) if l.isEmpty ⇒
            None
          case (Null, _) ⇒ None
          case (lst: Lst, headers) ⇒
            Some(
              applySelector(Obj(
                Map("top" → lst) ++
                  headers.link.map(kv ⇒ kv._1 → Text(kv._2.toURL())) ++
                  headers.get(Header.COUNT).map("count" → _).toMap
              ), context)
            )
          case (other, _) ⇒
            throw InternalServerError(ErrorBody(ErrorCode.RESOURCE_IS_NOT_COLLECTION, Some(s"$hrl: ${other.getClass}")))
        }


      case FetchFilter.EXPECTS_SINGLE_ITEM ⇒
        fetchResource(hrl, context).map {
          case Lst(l) if l.size == 1 ⇒ Some(applySelector(l.head, context))
          case Lst(l) if l.isEmpty ⇒
            throw NotFound(ErrorBody(ErrorCode.SINGLE_ITEM_NOT_FOUND, Some(s"$hrl")))
          case Lst(_) ⇒
            throw InternalServerError(ErrorBody(ErrorCode.SINGLE_ITEM_AMBIGUOUS, Some(s"$hrl")))
          case _: Obj ⇒
            throw InternalServerError(ErrorBody(ErrorCode.RESOURCE_IS_NOT_COLLECTION, Some(s"$hrl")))
        }

      case FetchFilter.EXPECTS_DOCUMENT ⇒
        fetchResource(hrl, context).map { resource =>
          Some(applySelector(resource, context))
        }
    }
  }

  protected def fetchResource(hrl: HRL, context: ExpressionEvaluatorContext)
                             (implicit mcx: MessagingContext): Task[Value] = {
    fetchResourceWithHeaders(hrl, context).map(_._1)
  }

  protected def fetchResourceWithHeaders(hrl: HRL, context: ExpressionEvaluatorContext)
                                        (implicit mcx: MessagingContext): Task[(Value, ResponseHeaders)] = {
    hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map { r =>
      val localized = if (annotation.localize) {
        val acceptLanguage = context.requestContext.httpHeaders.get("accept-language").map(_.toString).getOrElse(defaultLocale)
        val lr = LanguageRanges(acceptLanguage)
        ValueI18N.localize(r.body.content, lr, postfix=postfix)
      } else {
        r.body.content
      }
      (localized, r.headers)
    }
  }

  protected def handleError(s: String, context: ExpressionEvaluatorContext, e: Throwable): Task[Option[Value]] = {
    import FetchFilter._
    if (e.isInstanceOf[NotFound[_]])
      logger.trace(s"$s is not found", e)
    else
      logger.debug(s"Can't fetch $s", e)
    if (annotation.onError == ON_ERROR_REMOVE) {
      Task.now(None)
    } else if (annotation.onError == ON_ERROR_FAIL) {
      Task.raiseError(e)
    } else { // annotation.onError == ON_ERROR_DEFAULT
      {e match {
        case h: HyperbusError[_] ⇒
          defaultValue(h.headers.statusCode, context)
        case _ ⇒
          Task.now(annotation.default.get("*").map { defV ⇒
            expressionEvaluator.evaluate(context, defV)
          })
      }} flatMap {
        case None => Task.raiseError(e)
        case other => Task.now(other)
      }
    }
  }

  private def defaultValue(statusCode: Int, context: ExpressionEvaluatorContext): Task[Option[Value]] = Task.now {
    annotation.default.get(statusCode.toString).map { defV ⇒
      Some(expressionEvaluator.evaluate(context, defV))
    } getOrElse {
      annotation.default.get("*").map { defV ⇒
        expressionEvaluator.evaluate(context, defV)
      }
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

object FetchFilter {
  final val EXPECTS_COLLECTION_LINK = "collection_link"
  final val EXPECTS_COLLECTION_TOP = "collection_top"
  final val EXPECTS_SINGLE_ITEM = "single_item"
  final val EXPECTS_DOCUMENT = "document"

  final val ON_ERROR_FAIL = "fail"
  final val ON_ERROR_REMOVE = "remove"
  final val ON_ERROR_DEFAULT = "default"
}