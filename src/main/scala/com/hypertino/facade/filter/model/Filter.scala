/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.model

import com.hypertino.binders.value.{DefaultValueSerializerFactory, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext, PreparedExpression}
import com.hypertino.facade.raml.{RamlAnnotation, RamlFieldAnnotation}
import com.hypertino.inflector.naming.CamelCaseToSnakeCaseConverter

trait Filter {
  protected def expressionEvaluator: ExpressionEvaluator
  def timer: Option[String]
  def evaluatePredicate(expressionEvaluatorContext: ExpressionEvaluatorContext, expression: PreparedExpression): Boolean = {
    expressionEvaluator.evaluatePredicate(expressionEvaluatorContext, expression)
  }
}

trait RamlFilterFactory {
  protected implicit val defaultValueSerializerFactory = new DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type]
  def createFilters(target: RamlFilterTarget): SimpleFilterChain
  def createRamlAnnotation(name: String, value: Value): RamlAnnotation
  protected def predicateEvaluator: ExpressionEvaluator

  def createFilterChain(target: RamlFilterTarget): SimpleFilterChain = {
    val rawFilterChain = createFilters(target)
    SimpleFilterChain (
      requestFilters = rawFilterChain.requestFilters.map(proxifyRequestFilters(_, target)),
      responseFilters = rawFilterChain.responseFilters.map(proxifyResponseFilters(_, target)),
      eventFilters = rawFilterChain.eventFilters.map(proxifyEventFilters(_, target))
    )
  }

  def proxifyRequestFilters(filter: RequestFilter, ramlTarget: RamlFilterTarget): RequestFilter = {
    ConditionalRequestFilterProxy(ramlTarget.annotation, filter, predicateEvaluator)
  }

  def proxifyResponseFilters(filter: ResponseFilter, ramlTarget: RamlFilterTarget): ResponseFilter = {
    ConditionalResponseFilterProxy(ramlTarget.annotation, filter, predicateEvaluator)
  }

  def proxifyEventFilters(filter: EventFilter, ramlTarget: RamlFilterTarget): EventFilter = {
    ConditionalEventFilterProxy(ramlTarget.annotation, filter, predicateEvaluator)
  }
}

trait RamlFieldFilterFactory {
  protected implicit val defaultValueSerializerFactory = new DefaultValueSerializerFactory[CamelCaseToSnakeCaseConverter.type]
  def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation
  def createFieldFilter(fieldName: String, fieldTypeName: String, annotation: RamlFieldAnnotation): FieldFilter
}

sealed trait RamlFilterTarget {
  def annotation: RamlAnnotation
}

case class ResourceTarget(uri: String, annotation: RamlAnnotation) extends RamlFilterTarget

case class MethodTarget(uri: String, method: String, annotation: RamlAnnotation) extends RamlFilterTarget
