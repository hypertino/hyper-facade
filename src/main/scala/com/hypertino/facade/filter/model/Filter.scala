package com.hypertino.facade.filter.model

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Field, RamlAnnotation}

trait Filter {
  protected def expressionEvaluator: ExpressionEvaluator
  def evaluatePredicate(contextWithRequest: RequestContext, expression: PreparedExpression): Boolean = {
    expressionEvaluator.evaluatePredicate(contextWithRequest, expression)
  }
}

trait RamlFilterFactory {
  import com.hypertino.facade.filter.model.RamlTarget.annotations

  def createFilters(target: RamlTarget): SimpleFilterChain
  protected def predicateEvaluator: ExpressionEvaluator

  final def createFilterChain(target: RamlTarget): SimpleFilterChain = {
    val rawFilterChain = createFilters(target)
    SimpleFilterChain (
      requestFilters = proxifyRequestFilters(rawFilterChain.requestFilters, target),
      responseFilters = proxifyResponseFilters(rawFilterChain.responseFilters, target),
      eventFilters = proxifyEventFilters(rawFilterChain.eventFilters, target)
    )
  }

  def proxifyRequestFilters(rawFilters: Seq[RequestFilter], ramlTarget: RamlTarget): Seq[RequestFilter] = {
    val l = rawFilters.foldLeft(Seq.newBuilder[RequestFilter]) { (proxifiedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(proxifiedFilters) { (proxifiedFilters, annotation) ⇒
        proxifiedFilters += ConditionalRequestFilterProxy(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
    l
  }

  def proxifyResponseFilters(rawFilters: Seq[ResponseFilter], ramlTarget: RamlTarget): Seq[ResponseFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[ResponseFilter]) { (proxifiedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(proxifiedFilters) { (proxifiedFilters, annotation) ⇒
        proxifiedFilters += ConditionalResponseFilterProxy(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
  }

  def proxifyEventFilters(rawFilters: Seq[EventFilter], ramlTarget: RamlTarget): Seq[EventFilter] = {
    rawFilters.foldLeft(Seq.newBuilder[EventFilter]) { (proxifiedFilters, rawFilter) ⇒
      annotations(ramlTarget).foldLeft(proxifiedFilters) { (proxifiedFilters, annotation) ⇒
        proxifiedFilters += ConditionalEventFilterProxy(annotation, rawFilter, predicateEvaluator)
      }
    }.result()
  }
}

sealed trait RamlTarget
object RamlTarget {
  def annotations(ramlTarget: RamlTarget): Seq[RamlAnnotation] = {
    ramlTarget match {
      case TargetResource(_, ann) ⇒
        Seq(ann)
      case TargetMethod(_, _, ann) ⇒
        Seq(ann)
      case TargetField(_, field) ⇒
        field.annotations.foldLeft(Seq.newBuilder[RamlAnnotation]) { (ramlAnnotations, fieldAnnotation) ⇒
          ramlAnnotations += fieldAnnotation
        }.result()
    }
  }
}

case class TargetResource(uri: String, annotation: RamlAnnotation) extends RamlTarget
case class TargetMethod(uri: String, method: String, annotation: RamlAnnotation) extends RamlTarget
case class TargetField(typeName: String, field: Field) extends RamlTarget