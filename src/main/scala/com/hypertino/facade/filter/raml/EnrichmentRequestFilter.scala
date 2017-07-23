package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Text, Value}
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PredicateEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.{EnrichAnnotation, Field, RamlAnnotation}
import com.hypertino.hyperbus.model.DynamicBody
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.Map
import scala.concurrent.{ExecutionContext, Future}

class EnrichRequestFilter(field: Field, protected val predicateEvaluator: PredicateEvaluator) extends RequestFilter {
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
        annotation.name match {
          case RamlAnnotation.CLIENT_IP ⇒
            addField(ramlField.name, Text(context.remoteAddress), fields)

          case RamlAnnotation.CLIENT_LANGUAGE ⇒
            context.originalHeaders.get(FacadeHeaders.ACCEPT_LANGUAGE) match {
              case Some(Text(value)) ⇒
                addField(ramlField.name, Text(value), fields)  // todo: format of header?

              case _ ⇒
                enrichedFields  // do nothing, because header is missing
            }

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

class EnrichmentFilterFactory(protected val predicateEvaluator: PredicateEvaluator) extends RamlFilterFactory {
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
        log.warn(s"Annotations 'x-client-ip' and 'x-client-language' are not supported for target $unknownTarget. Empty filter chain will be created")
        FilterChain.empty
    }
  }

  def createRequestFilters(field: Field): Seq[EnrichRequestFilter] = {
    field.annotations.foldLeft(Seq.newBuilder[EnrichRequestFilter]) { (filters, annotation) ⇒
        annotation match {
          case EnrichAnnotation(_, _) ⇒
            filters += new EnrichRequestFilter(field, predicateEvaluator)
          case _ ⇒
            filters
        }
    }.result()
  }
}
