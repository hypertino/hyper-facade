package com.hypertino.facade.filter.parser

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.facade.model.ContextStorage._
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{ErrorBody, InternalServerError}
import com.hypertino.parser.HEval
import com.hypertino.parser.ast.Identifier
import com.hypertino.parser.eval.ValueContext
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class PredicateEvaluator {
  import PredicateEvaluator._

  val log = LoggerFactory.getLogger(getClass)

  def evaluate(predicate: String, contextWithRequest: ContextWithRequest): Boolean = {
    val context = new ValueContext(contextWithRequest.toObj) {
      override def binaryOperation: PartialFunction[(Value, Identifier, Value), Value] = IpParser.binaryOperation
      override def customOperators = Seq(IpParser.IP_MATCHES)
    }
    val result = try {
      HEval(predicate, context).toBoolean
    }
    catch {
      case NonFatal(ex) ⇒
        implicit val mcx = contextWithRequest.request
        val errorBody = ErrorBody("condition-check-failure") // todo: add check line num
        log.error(s"Predicate check '$predicate' failed #${errorBody.errorId}", ex)
        throw InternalServerError(errorBody)
    }
    if (log.isTraceEnabled) {
      log.trace(s"Checking $predicate is $result. Context: ${context.obj}")
    }
    result
  }
}

object PredicateEvaluator {
  def apply(): PredicateEvaluator = {
    new PredicateEvaluator
  }

  implicit class ObjectGenerator(contextWithRequest: ContextWithRequest) {
    def toObj: Obj = {
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
}
