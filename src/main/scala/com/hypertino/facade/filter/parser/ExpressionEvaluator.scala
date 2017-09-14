package com.hypertino.facade.filter.parser

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError}
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import com.hypertino.parser.ast.Identifier
import com.hypertino.parser.eval.Context
import com.hypertino.parser.{HEval, HParser}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

case class PreparedExpression(source: String, ast: com.hypertino.parser.ast.Expression)

case class ExpressionEvaluatorContext(requestContext: RequestContext, extraContext: Value) extends Context{
  private lazy val obj = preparePredicateContext(requestContext) % extraContext

  override def identifier = {
    case identifier ⇒ obj(identifier.segments.map(Text))
  }
  override def binaryOperation: PartialFunction[(Value, Identifier, Value), Value] = IpParser.binaryOperation
  override def customOperators = Seq(IpParser.IP_MATCHES)
  override def function: PartialFunction[(Identifier, Seq[Value]), Value] = {
    case (Identifier(Seq("newId")), _) ⇒ IdGenerator.create()
    case (Identifier(Seq("newSeq")), _) ⇒ SeqGenerator.create()
  }
  override def unaryOperation = Map.empty
  override def binaryOperationLeftArgument = Map.empty

  protected def preparePredicateContext(contextWithRequest: RequestContext): Obj = {
    val request = contextWithRequest.request
    Obj.from(
      "context" → contextWithRequest.contextStorage,
      "headers" → Obj(request.headers),
      "location" → request.headers.hrl.location,
      "query" → request.headers.hrl.query,
      "method" → request.headers.method,
      "body" → request.body.content,
      "remote_address" → contextWithRequest.remoteAddress
    )
  }
}

object PreparedExpression {
  def apply(source: String): PreparedExpression = PreparedExpression(source, HParser(source))
}

trait ExpressionEvaluator {
  protected val log = LoggerFactory.getLogger(getClass)

  def evaluatePredicate(context: ExpressionEvaluatorContext, expression: PreparedExpression): Boolean = {
    val result = try {
      evaluate(context, expression).toBoolean
    }
    catch {
      case NonFatal(ex) ⇒
        implicit val mcx = context.requestContext.request
        val errorBody = ErrorBody("condition-check-failure") // todo: add check line num
        log.error(s"Predicate check '${expression.source}' failed #${errorBody.errorId}", ex)
        throw InternalServerError(errorBody)
    }
    if (log.isTraceEnabled) {
      log.trace(s"Checking ${expression.source} is $result. Context: ${context.requestContext}")
    }
    result
  }

  def evaluate(context: ExpressionEvaluatorContext, expression: PreparedExpression): Value = {
    new HEval(context).eval(expression.ast)
  }
}

object DefaultExpressionEvaluator extends ExpressionEvaluator