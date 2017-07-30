package com.hypertino.facade.integration

import com.hypertino.binders.value.Obj
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Method, NoContent, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.io.Source
import scala.util.Success

class SimpleHttpTest extends TestBase("inproc-test.conf") {
  facadeService // initialize

  "Facade" should "serve http resource" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("integerField" → 100500, "textField" → "Yey")))
        })
        Continue
      }
    }

    Source.fromURL("http://localhost:54321/simple-resource", "UTF-8").mkString shouldBe """{"textField":"Yey","integerField":100500}"""
  }

  it should "serve resource with pattern" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service/{id}", Method.GET, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(DynamicBody(Obj.from("integerField" → command.request.headers.hrl.query.id.toLong, "textField" → "Yey")))
        })
        Continue
      }
    }

    Source.fromURL("http://localhost:54321/simple-resource/100500", "UTF-8").mkString shouldBe """{"textField":"Yey","integerField":100500}"""
  }
}
