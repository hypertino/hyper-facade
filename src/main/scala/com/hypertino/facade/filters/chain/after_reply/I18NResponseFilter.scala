/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.chain.after_reply

import com.hypertino.binders.value.{Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model.ResponseFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.RequestContext
import com.hypertino.hyperbus.model.{DynamicBody, DynamicResponse, StandardResponse}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import spray.http.parser.HttpParser

import scala.util.control.NonFatal

class I18NResponseFilter(
                          protected val config: Config,
                          protected val expressionEvaluator: ExpressionEvaluator
                        ) extends ResponseFilter with StrictLogging {

  private val defaultLocale = config.getString("hyperfacade.i18n-filter.default-locale")
  private val postfix = config.getString("hyperfacade.i18n-filter.fields-postfix")
  val timer = Some(MetricKeys.specificFilter("I18NResponseFilter"))

  override def apply(requestContext: RequestContext, response: DynamicResponse)
                    (implicit scheduler: Scheduler): Task[DynamicResponse] = {
    Task.now {
      try {
        requestContext.request.headers.hrl.query.dynamic.i18n match {
          case Text("only") ⇒
            response

          case Text("all") ⇒
            val bodyContent = I18NResponseFilter.filterFields(response.body.content, getRequestLanguages(requestContext), includeAll = true, postfix)
            StandardResponse(DynamicBody(bodyContent), response.headers).asInstanceOf[DynamicResponse]

          case _ ⇒
            val bodyContent = I18NResponseFilter.filterFields(response.body.content, getRequestLanguages(requestContext), includeAll = false, postfix)
            StandardResponse(DynamicBody(bodyContent), response.headers).asInstanceOf[DynamicResponse]
        }
      }
      catch {
        case NonFatal(e) ⇒
          logger.error("Unhandled exception", e)
          throw e;
      }
    }
  }

  private def getRequestLanguages(requestContext: RequestContext): Seq[String] = {
    import spray.http.HttpHeaders._
    val locale = requestContext.httpHeaders.get("accept-language").map(_.toString).getOrElse(defaultLocale)
    HttpParser.parseHeader(RawHeader("accept-language", locale)) match {
      case Right(`Accept-Language`(acceptLangs)) ⇒
        acceptLangs.sortBy(_.qValue).reverse.flatMap { l ⇒
          List(l.primaryTag) ++ l.subTags
        } :+ defaultLocale

      case _ ⇒ Seq(defaultLocale)
    }
  }
}

object I18NResponseFilter {
  def filterFields(v: Value, languages: Seq[String], includeAll: Boolean, postfix: String): Value = {
    recursiveFilterFields(v, languages, includeAll, postfix)
  }

  private def recursiveFilterFields(original: Value, languages: Seq[String], includeAll: Boolean, postfix: String): Value = {
    original match {
      case Obj(inner) ⇒
        val patch = Obj(inner.filter(kv ⇒ kv._1.endsWith(postfix) && kv._2.isInstanceOf[Obj]).flatMap { case (k, v) ⇒
          val i = v.asInstanceOf[Obj]
          var l10n: Value = null
          val it = languages.iterator
          while (it.hasNext && l10n == null) {
            val lang = it.next()
            i.v.get(lang).foreach { v ⇒
              l10n = v
            }
          }
          if (l10n != null) {
            Some(k.substring(0, k.length - postfix.length) → l10n)
          }
          else {
            None
          }
        })

        if (includeAll) {
          original % patch
        }
        else {
          Obj(inner.filterNot(_._1.endsWith(postfix))) % patch
        }

      case Lst(inner) ⇒
        Lst(
          inner.map { i ⇒
            recursiveFilterFields(i, languages, includeAll, postfix)
          }
        )

      case _ ⇒ original
    }
  }
}