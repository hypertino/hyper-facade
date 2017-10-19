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
                                   expects: String, //todo: this should be enum
                                   onError: String, //todo: this should be enum
                                   defaultStatuses: Set[Int],
                                   selector: Option[PreparedExpression],
                                   default: Option[PreparedExpression]
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
