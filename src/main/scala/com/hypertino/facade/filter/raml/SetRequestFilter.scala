package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model._
import com.hypertino.facade.raml.SetAnnotation
import com.hypertino.facade.utils.RequestUtils
import com.hypertino.hyperbus.model.{HRL, Headers}
import com.hypertino.parser.{HParser, ast}

import scala.concurrent.{ExecutionContext, Future}

class SetRequestFilter(set: SetAnnotation,
                       protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  private val target = set.target.getOrElse(throw new IllegalArgumentException(s"target is not defined for $set"))
  private val targetIdentifier = HParser(target) match {
    case i: ast.Identifier ⇒ i
    case other ⇒ throw new IllegalArgumentException(s"target is not identifier: $other")
  }

  private def mergeObj(existing: Obj, path: Seq[String], result: Value): Obj = {
    if (path.isEmpty) {
      result match {
        case o: Obj ⇒ o
        case Null ⇒ Obj.empty
        case other ⇒ throw new IllegalArgumentException(s"Object is expected: $other")
      }
    }
    else {
      val patch = Obj.innerValue(path, result)
      existing % patch
    }
  }

  private def setContext(contextWithRequest: RequestContext, path: Seq[String], result: Value): RequestContext = {
    contextWithRequest.copy(contextStorage = mergeObj(contextWithRequest.contextStorage,path,result))
  }

  private def setHeader(contextWithRequest: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val headersObj = mergeObj(
      Obj(contextWithRequest.request.headers.underlying),
      path,
      result
    )

    val request = contextWithRequest.request.copy(headers=Headers.builder.++=(headersObj).requestHeaders())
    contextWithRequest.copy(request=request)
  }

  private def setLocation(contextWithRequest: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val hrl = HRL.fromURL(result.toString)
    val request = RequestUtils.copyWith(contextWithRequest.request, hrl)
    contextWithRequest.copy(request=request)
  }

  private def setQuery(contextWithRequest: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val existingHrl = contextWithRequest.request.headers.hrl
    val existingQuery = existingHrl.query match {
      case o: Obj ⇒ o
      case Null ⇒ Obj.empty
    }
    val newQuery = mergeObj(existingQuery,path,result) match {
      case o: Obj if o.isEmpty ⇒ Null
      case other ⇒ other
    }

    val hrl = HRL(existingHrl.location, newQuery)
    val request = RequestUtils.copyWith(contextWithRequest.request, hrl)
    contextWithRequest.copy(request=request)
  }

  private def setMethod(contextWithRequest: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val hrl = contextWithRequest.request.headers.hrl
    val request = RequestUtils.copyWith(contextWithRequest.request, hrl, Some(result.toString))
    contextWithRequest.copy(request=request)
  }

  override def apply(contextWithRequest: RequestContext)
                    (implicit ec: ExecutionContext): Future[RequestContext] = {
    Future {
      val result = expressionEvaluator.evaluate(ExpressionEvaluatorContext(contextWithRequest, Null), set.source)

      targetIdentifier.segments.head match {
        case "context" ⇒
          setContext(contextWithRequest, targetIdentifier.segments.tail, result)

        case "headers" =>
          setHeader(contextWithRequest, targetIdentifier.segments.tail, result)

        case "location" ⇒
          setLocation(contextWithRequest, targetIdentifier.segments.tail, result)

        case "query" ⇒
          setQuery(contextWithRequest, targetIdentifier.segments.tail, result)

        case "method" ⇒
          setMethod(contextWithRequest, targetIdentifier.segments.tail, result)

        case other ⇒
          throw new IllegalArgumentException(s"Can't set unknown variable '$other'")
      }
    }
  }
}


