package com.hypertino.facade.filter.parser

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.facade.model.ContextStorage._
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError}
import com.hypertino.parser.{HEval, HParser}
import com.hypertino.parser.ast.Identifier
import com.hypertino.parser.eval.ValueContext
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

case class PreparedExpression(source: String, ast: com.hypertino.parser.ast.Expression)

object PreparedExpression {
  def apply(source: String): PreparedExpression = PreparedExpression(source, HParser(source))
}

trait PredicateEvaluator {
  protected val log = LoggerFactory.getLogger(getClass)

  def evaluate(contextWithRequest: ContextWithRequest, expression: PreparedExpression): Boolean = {
    val context = new ValueContext(toObj(contextWithRequest)) {
      override def binaryOperation: PartialFunction[(Value, Identifier, Value), Value] = IpParser.binaryOperation
      override def customOperators = Seq(IpParser.IP_MATCHES)
    }
    val result = try {
      new HEval(context).eval(expression.ast).toBoolean
    }
    catch {
      case NonFatal(ex) ⇒
        implicit val mcx = contextWithRequest.request
        val errorBody = ErrorBody("condition-check-failure") // todo: add check line num
        log.error(s"Predicate check '${expression.source}' failed #${errorBody.errorId}", ex)
        throw InternalServerError(errorBody)
    }
    if (log.isTraceEnabled) {
      log.trace(s"Checking ${expression.source} is $result. Context: ${context.obj}")
    }
    result
  }

  protected def toObj(contextWithRequest: ContextWithRequest): Obj = {
    val valueMap = Map.newBuilder[String, Value]
    val request = contextWithRequest.request

    val contextMap = Map[String, Value](
      ContextStorage.AUTH_USER → contextWithRequest.authUser.toValue,
      ContextStorage.IS_AUTHORIZED → contextWithRequest.isAuthorized.toValue,
      "ip" → contextWithRequest.remoteAddress
    )
    valueMap += ("context" → contextMap)
    valueMap += ("headers" → Obj(request.headers)) // todo: translate 'short' headers!
    valueMap += "location" → request.headers.hrl.location
    valueMap += "query" → request.headers.hrl.query
    valueMap += "body" → request.body.content
    Obj(valueMap.result())
  }
}

object DefaultPredicateEvaluator extends PredicateEvaluator