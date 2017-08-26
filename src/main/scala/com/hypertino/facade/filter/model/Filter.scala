package com.hypertino.facade.filter.model

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, RamlAnnotation, RamlFieldAnnotation, TypeDefinition}

trait Filter {
  protected def expressionEvaluator: ExpressionEvaluator
  def evaluatePredicate(contextWithRequest: RequestContext, extraContext: Value, expression: PreparedExpression): Boolean = {
    expressionEvaluator.evaluatePredicate(contextWithRequest, extraContext, expression)
  }
}

trait RamlFilterFactory {
  def createFilters(target: RamlFilterTarget): SimpleFilterChain
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
  def createFieldFilter(fieldName: String, fieldTypeName: String, annotation: RamlFieldAnnotation): FieldFilter
}

sealed trait RamlFilterTarget {
  def annotation: RamlAnnotation
}

case class ResourceTarget(uri: String, annotation: RamlAnnotation) extends RamlFilterTarget

case class MethodTarget(uri: String, method: String, annotation: RamlAnnotation) extends RamlFilterTarget
