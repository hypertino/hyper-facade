package com.hypertino.facade.integration

import com.hypertino.binders.value.{Lst, Number, Obj}
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.model.{Created, DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, HRL, Header, Headers, Method, NoContent, Ok}
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

  // todo: fix
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

  // todo: fix
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

  // todo: fix
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

  // todo: fix
  ignore should "foward (eval)" in {
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

    httpGet("http://localhost:54321/simple-forward") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }

  // todo: fix
  ignore should "render link" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          val headers = Headers
            .builder
            .+=(Header.COUNT → Number(3))
            .withLink(Map("next_page_url" → HRL("hb://test-service/{id}", Obj.from("id" → "100500"))))
            .result()

          Ok(DynamicBody(Lst.from("a","b","c")), headers)
        })
        Continue
      }
    }

    val r = httpGetResponse("http://localhost:54321/simple-resource")
    r.getResponseBody shouldBe """["a","b","c"]"""
    import scala.collection.JavaConverters._
    r.getHeaders("Link").asScala.head shouldBe "</simple-resource/100500?>; rel=next_page_url"
    r.getHeaders("X-Count").asScala.head shouldBe "3"
  }

  // todo: fix
  ignore should "render wrap collection" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          val headers = Headers
            .builder
            .+=(Header.COUNT → Number(3))
            .withLink(Map("next_page_url" → HRL("hb://test-service/{id}", Obj.from("id" → "100500"))))
            .result()

          Ok(DynamicBody(Lst.from("a","b","c")), headers)
        })
        Continue
      }
    }

    val r = httpGetResponse("http://localhost:54321/simple-resource?wrap_collection=1")
    r.getResponseBody shouldBe """{"count":3,"link":{"next_page_url":"/simple-resource/100500"},"items":["a","b","c"]}"""
  }
}
