package com.hypertino.facade.filter

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._

import scala.concurrent.{ExecutionContext, Future}

class NoOpFilterFactory extends RamlFilterFactory {
  val predicateEvaluator = PredicateEvaluator()

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    SimpleFilterChain(
      requestFilters = Seq.empty,
      responseFilters = Seq(new NoOpFilter(target)),
      eventFilters = Seq.empty
    )
  }
}

class NoOpFilter(target: RamlTarget) extends ResponseFilter {
  override def apply(contextWithRequest: ContextWithRequest, output: FacadeResponse)
                    (implicit ec: ExecutionContext): Future[FacadeResponse] = {
    Future.successful(output)
  }
  override def toString = s"NoOpFilter@${this.hashCode}/$target"
}
