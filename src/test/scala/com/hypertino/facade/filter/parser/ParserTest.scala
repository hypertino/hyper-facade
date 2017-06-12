package com.hypertino.facade.filter.parser

import com.hypertino.authentication.AuthUser
import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.Method
import com.hypertino.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class ParserTest extends FreeSpec with Matchers {

  "PredicateEvaluator" - {
    "ip in range" in {
      val request = FacadeRequest(
        Uri("/auth-resource"),
        Method.GET,
        Map.empty,
        Map("field" → Text("value"))
      )
      val context = FacadeRequestContext("109.207.13.2", spray.http.Uri.Empty, "path", "get", Map.empty, None, Map(
        ContextStorage.IS_AUTHORIZED → true,
        ContextStorage.AUTH_USER → AuthUser("id", Set("qa"), Null)
      ))
      val cwr = ContextWithRequest(context, request)

      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.13.0 - 109.207.13.255"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.13.2" ip matches "109.207.13.0 - 109.207.13.255"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" context.ip ip matches "109.207.10.0 - 109.207.13.1"""", cwr) shouldBe false
      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.13.0/24"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""""109.207.13.255" ip matches "109.207.13.0/24"""", cwr) shouldBe true
      PredicateEvaluator().evaluate(""" "109.207.14.0" ip matches "109.207.13.0/24"""", cwr) shouldBe false
      PredicateEvaluator().evaluate("""context.ip ip matches "109.207.12.0/24"""", cwr) shouldBe false
    }
  }
}

