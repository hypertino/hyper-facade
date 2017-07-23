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

  def evaluate(contextWithRequest: RequestContext, expression: PreparedExpression): Boolean = {
    val context = new ValueContext(preparePredicateContext(contextWithRequest)) {
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

  protected def preparePredicateContext(contextWithRequest: RequestContext): Obj = {
    val request = contextWithRequest.request
    Obj.from(
      "context" → contextWithRequest.contextStorage,
      "headers" → Obj(request.headers),
      "location" → request.headers.hrl.location,
      "query" → request.headers.hrl.query,
      "body" → request.body.content,
      "remote_address" → contextWithRequest.remoteAddress
    )
  }
}

object DefaultPredicateEvaluator extends PredicateEvaluator