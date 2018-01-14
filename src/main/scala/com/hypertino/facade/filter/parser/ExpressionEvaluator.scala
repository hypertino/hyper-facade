/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.parser

import com.hypertino.binders.core.ImplicitDeserializer
import com.hypertino.binders.value.{Obj, _}
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError}
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import com.hypertino.parser.ast.Identifier
import com.hypertino.parser.eval.Context
import com.hypertino.parser.{HEval, HParser}
import com.typesafe.scalalogging.StrictLogging

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
    case (Identifier(Seq("new_id")), _) ⇒ IdGenerator.create()
    case (Identifier(Seq("new_seq")), _) ⇒ SeqGenerator.create()
  }
  override def unaryOperation = Map.empty
  override def binaryOperationLeftArgument = Map.empty

  protected def preparePredicateContext(requestContext: RequestContext): Obj = {
    val request = requestContext.request
    // todo: make this lazy evaluated?
    Obj.from(
      "context" → requestContext.contextStorage,
      "headers" → Obj(request.headers),
      "http_headers" → Obj(requestContext.httpHeaders),
      "cookies" → requestContext.cookies,
      "location" → request.headers.hrl.location,
      "query" → request.headers.hrl.query,
      "method" → request.headers.method,
      "body" → request.body.content,
      "http_body" -> requestContext.payload.map(Text).getOrElse(Null),
      "remote_address" → requestContext.remoteAddress
    )
  }
}

object PreparedExpression {
  def apply(source: String): PreparedExpression = PreparedExpression(source, HParser(source))

  implicit object PreparedExpressionDeserializer extends ImplicitDeserializer[PreparedExpression, ValueDeserializer[_]] {
    override def read(deserializer: ValueDeserializer[_]): PreparedExpression = PreparedExpression(deserializer.readString())
  }
}

trait ExpressionEvaluator extends StrictLogging {
  def evaluatePredicate(context: ExpressionEvaluatorContext, expression: PreparedExpression): Boolean = {
    val result = try {
      evaluate(context, expression).toBoolean
    }
    catch {
      case NonFatal(ex) ⇒
        implicit val mcx = context.requestContext.request
        val errorBody = ErrorBody(ErrorCode.CONDITION_CHECK_FAILURE) // todo: add check line num
        logger.error(s"Predicate check '${expression.source}' failed #${errorBody.errorId}", ex)
        throw InternalServerError(errorBody)
    }
    logger.trace(s"Checking ${expression.source} is $result. Context: $context")
    result
  }

  def evaluate(context: ExpressionEvaluatorContext, expression: PreparedExpression): Value = {
    new HEval(context).eval(expression.ast)
  }
}

object DefaultExpressionEvaluator extends ExpressionEvaluator