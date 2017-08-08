package com.hypertino.facade.integration

import com.hypertino.binders.value.{Lst, Obj}
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Method, NoContent, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.util.Success

class SimpleHttpTest extends TestBase("inproc-test.conf") {
  "Facade" should "serve http resource" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → 100500, "text_field" → "Yey")))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/simple-resource") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }

  ignore should "filter fields" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → 100500, "text_field" → "Yey")))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/simple-resource?fields=text_field") shouldBe """{"text_field":"Yey"}"""
  }

  ignore should "filter collection fields" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(
            Lst.from(
              Obj.from("integer_field" → 100500, "text_field" → "Yey"),
              Obj.from("integer_field" → 100501, "text_field" → "Hey")
            )
          ))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/simple-resource?fields=text_field") shouldBe """[{"text_field":"Yey"},{"text_field":"Hey"}]"""
  }

  ignore should "serve resource with pattern" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service/{id}", Method.GET, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → command.request.headers.hrl.query.id.toLong, "text_field" → "Yey")))
        })
        Continue
      }
    }

    httpGet("http://localhost:54321/simple-resource/100500") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }
}
