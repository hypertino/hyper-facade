package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj, Value}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.{ExpressionEvaluator, ExpressionEvaluatorContext}
import com.hypertino.facade.model._
import com.hypertino.facade.utils.RequestUtils
import com.hypertino.hyperbus.model.{HRL, MessageHeaders}
import com.hypertino.parser.{HParser, ast}
import monix.eval.Task
import monix.execution.Scheduler

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

  private def setContext(requestContext: RequestContext, path: Seq[String], result: Value): RequestContext = {
    requestContext.copy(contextStorage = mergeObj(requestContext.contextStorage,path,result))
  }

  private def setHeader(requestContext: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val headersObj = mergeObj(
      Obj(requestContext.request.headers.underlying),
      path,
      result
    )

    val request = requestContext.request.copy(headers=MessageHeaders.builder.++=(headersObj).requestHeaders())
    requestContext.copy(request=request)
  }

  private def setLocation(requestContext: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val hrl = HRL.fromURL(result.toString)
    val request = RequestUtils.copyWith(requestContext.request, hrl)
    requestContext.copy(request=request)
  }

  private def setQuery(requestContext: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val existingHrl = requestContext.request.headers.hrl
    val existingQuery = existingHrl.query match {
      case o: Obj ⇒ o
      case _ ⇒ Obj.empty
    }
    val newQuery = mergeObj(existingQuery,path,result) match {
      case o: Obj if o.isEmpty ⇒ Null
      case other ⇒ other
    }

    val hrl = HRL(existingHrl.location, newQuery)
    val request = RequestUtils.copyWith(requestContext.request, hrl)
    requestContext.copy(request=request)
  }

  private def setMethod(requestContext: RequestContext, path: Seq[String], result: Value): RequestContext = {
    val hrl = requestContext.request.headers.hrl
    val request = RequestUtils.copyWith(requestContext.request, hrl, Some(result.toString))
    requestContext.copy(request=request)
  }

  override def apply(requestContext: RequestContext)
                    (implicit scheduler: Scheduler): Task[RequestContext] = {
    Task.now {
      val result = expressionEvaluator.evaluate(ExpressionEvaluatorContext(requestContext, Obj.empty), set.source)

      targetIdentifier.segments.head match {
        case "context" ⇒
          setContext(requestContext, targetIdentifier.segments.tail, result)

        case "headers" =>
          setHeader(requestContext, targetIdentifier.segments.tail, result)

        case "location" ⇒
          setLocation(requestContext, targetIdentifier.segments.tail, result)

        case "query" ⇒
          setQuery(requestContext, targetIdentifier.segments.tail, result)

        case "method" ⇒
          setMethod(requestContext, targetIdentifier.segments.tail, result)

        case other ⇒
          throw new IllegalArgumentException(s"Can't set unknown variable '$other'")
      }
    }
  }
}


