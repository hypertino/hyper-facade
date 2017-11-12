/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.Hyperbus
import monix.execution.Scheduler
import scaldi.Injectable

trait FetchAnnotationBase extends RamlAnnotation {
  def location: PreparedExpression
  def query: Map[String, PreparedExpression]
  def expects: String //todo: this should be enum
  def onError: String //todo: this should be enum
  def defaultStatuses: Set[Int]
  def selector: Option[PreparedExpression]
  def default: Option[PreparedExpression]
}

case class ContextFetchAnnotation(
                                   @fieldName("if") predicate: Option[PreparedExpression],
                                   target: String,
                                   location: PreparedExpression,
                                   query: Map[String, PreparedExpression],
                                   expects: String = FetchFilter.EXPECTS_DOCUMENT,   //todo: this should be enum
                                   onError: String = FetchFilter.ON_ERROR_FAIL,      //todo: this should be enum
                                   defaultStatuses: Set[Int] = Set(404),
                                   selector: Option[PreparedExpression] = None,
                                   default: Option[PreparedExpression] = None
                                 ) extends RamlAnnotation with FetchAnnotationBase {
  def name: String = "context_fetch"
}

class ContextFetchFilterFactory(hyperbus: Hyperbus,
                                protected val predicateEvaluator: ExpressionEvaluator,
                                protected implicit val scheduler: Scheduler) extends RamlFilterFactory with Injectable {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    val fa = target match {
      case ResourceTarget(_, fa : ContextFetchAnnotation) ⇒ fa
      case MethodTarget(_, _, fa : ContextFetchAnnotation) ⇒ fa
      case otherTarget ⇒ throw RamlConfigException(s"Annotation 'context_fetch' cannot be assigned to $otherTarget")
    }
    SimpleFilterChain(
      requestFilters = Seq(new ContextFetchRequestFilter(fa, hyperbus, predicateEvaluator,scheduler)),
      responseFilters = Seq.empty,
      eventFilters = Seq.empty
    )
  }

  override def createRamlAnnotation(name: String, value: Value): RamlAnnotation = {
    value.to[ContextFetchAnnotation]
  }
}

object ContextFetchFilter {
  final val EXPECTS_COLLECTION_LINK = "collection_link"
  final val EXPECTS_COLLECTION_TOP = "collection_top"
  final val EXPECTS_SINGLE_ITEM = "single_item"
  final val EXPECTS_DOCUMENT = "document"

  final val ON_ERROR_FAIL = "fail"
  final val ON_ERROR_REMOVE = "remove"
  final val ON_ERROR_DEFAULT = "default"
}