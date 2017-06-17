package com.hypertino.facade.filter.parser

import com.hypertino.binders.value.{Obj, _}
import com.hypertino.facade.model.ContextStorage._
import com.hypertino.facade.model._
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
    try {
      HEval(predicate, context).toBoolean
    }
    catch {
      case NonFatal(ex) ⇒
        log.error(s"predicate '$predicate' was parsed with error", ex)
        false
    }
  }
}

object PredicateEvaluator {
  def apply(): PredicateEvaluator = {
    new PredicateEvaluator
  }

  implicit class ObjectGenerator(contextWithRequest: ContextWithRequest) {
    def toObj: Obj = {
      val valueMap = Map.newBuilder[String, Value]
      val context = contextWithRequest.context
      val request = contextWithRequest.request

      val contextMap = Map[String, Value](
        ContextStorage.AUTH_USER → context.authUser.toValue,
        ContextStorage.IS_AUTHORIZED → context.isAuthorized.toValue,
        "ip" → context.remoteAddress
      )
      valueMap += ("context" → contextMap)
      valueMap += ("headers" → Obj(request.headers))
      valueMap += "resourceLocation" → 
      Obj(valueMap.result())
    }
  }
}
