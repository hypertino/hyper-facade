package com.hypertino.facade.integration

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Method, NoContent, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.io.Source
import scala.util.Success

class SimpleTest extends IntegrationTestBase("inproc-test.conf", "raml-configs/integration/http.raml") {

  "Integration. HTTP" - {
    "get resource" in {
      register {
        hyperbus.commands[DynamicRequest](
          DynamicRequest.requestMeta,
          DynamicRequestObservableMeta(RequestMatcher("/simple-service", Method.GET, None))
        ).subscribe { implicit request =>
          request.reply(Success {
            Ok(DynamicBody(Obj.from("integerField" → 100500, "textField" → "Yey")))
          })
          Continue
        }
      }

      Thread.sleep(1000)

      Source.fromURL("http://localhost:54321/inproc-test/simple-service", "UTF-8").mkString shouldBe """{"integerField":100500,"textField":"Yey"}"""
    }
  }
}
