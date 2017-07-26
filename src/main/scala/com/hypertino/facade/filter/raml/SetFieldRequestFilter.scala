package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{Field, SetAnnotation}
import com.hypertino.hyperbus.model.DynamicBody
import org.slf4j.LoggerFactory

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

class SetFieldRequestFilter(field: Field, protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {
  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val request = contextWithRequest.request
      val enrichedFields = enrichFields(field, request.body.content.toMap, contextWithRequest)
      contextWithRequest.copy(
        request = request.copy(body = DynamicBody(Obj(enrichedFields)))
      )
    }
  }

  private def enrichFields(ramlField: Field, fields: Map[String, Value], context: RequestContext): Map[String, Value] = {
      val annotations = ramlField.annotations
      annotations.foldLeft(fields) { (enrichedFields, annotation) ⇒
        annotation match {
          case set: SetAnnotation if set.predicate.forall(expressionEvaluator.evaluatePredicate(context, _)) ⇒
            addField(ramlField.name, expressionEvaluator.evaluate(context,set.source), fields)

          case _ ⇒
            enrichedFields// do nothing, this annotation doesn't belong to enrichment filter
        }
      }
  }

  def addField(pathToField: String, value: Value, requestFields: Map[String, Value]): Map[String, Value] = {
    if (pathToField.contains("."))
      pathToField.split('.').toList match {
        case (leadPathSegment :: _) ⇒
          requestFields.get(leadPathSegment) match {
            case Some(subFields) ⇒
              val tailPath = pathToField.substring(leadPathSegment.length + 1)
              requestFields + (leadPathSegment → Obj(addField(tailPath, value, subFields.toMap)))
          }
      }
    else
      requestFields + (pathToField → value)
  }
}

class SetFieldFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory {
  private val log = LoggerFactory.getLogger(getClass)

  override def createFilters(target: RamlTarget): SimpleFilterChain = {
    target match {
      case TargetField(_, field) ⇒
        SimpleFilterChain(
          requestFilters = createRequestFilters(field),
          responseFilters = Seq.empty,
          eventFilters = Seq.empty
        )
      case unknownTarget ⇒
        log.warn(s"Annotations 'set' is not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }

  def createRequestFilters(field: Field): Seq[SetFieldRequestFilter] = {
    field.annotations.foldLeft(Seq.newBuilder[SetFieldRequestFilter]) { (filters, annotation) ⇒
        annotation match {
          case _: SetAnnotation ⇒
            filters += new SetFieldRequestFilter(field, predicateEvaluator)
          case _ ⇒
            filters
        }
    }.result()
  }
}
