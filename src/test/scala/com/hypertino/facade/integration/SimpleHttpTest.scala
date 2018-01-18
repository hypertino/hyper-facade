/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.integration

import com.hypertino.binders.value.{Lst, Number, Obj}
import com.hypertino.facade.{TestBase, TestBaseWithFacade}
import com.hypertino.hyperbus.model.{Created, DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Found, HRL, Header, Headers, MessageHeaders, Method, NoContent, Ok, TemporaryRedirect}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.util.Success

class SimpleHttpTest extends TestBaseWithFacade("inproc-test.conf") {
  "Facade" should "serve http resource" in {
    val t = testObjects
    import t._
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

    httpGet(s"http://localhost:$httpPort/simple-resource") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }

  it should "redirect" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Found(EmptyBody, HRL.fromURL("http://ya.ru"))
        })
        Continue
      }
    }

    val r = httpGetResponse(s"http://localhost:$httpPort/simple-resource")
    r.getStatusCode shouldBe 302
    r.getHeader("Location") shouldBe "http://ya.ru"
  }

  it should "filter fields" in {
    val t = testObjects
    import t._
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

    httpGet(s"http://localhost:$httpPort/simple-resource?fields=text_field") shouldBe """{"text_field":"Yey"}"""
  }

  it should "filter collection fields" in {
    val t = testObjects
    import t._
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

    httpGet(s"http://localhost:$httpPort/simple-resource?fields=text_field") shouldBe """[{"text_field":"Yey"},{"text_field":"Hey"}]"""
  }

  it should "update fields according to i18n" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("title~i18n" → Obj.from("ru" → "Привет", "fr" → "Bonjour"))))
        })
        Continue
      }
    }

    httpGet(s"http://localhost:$httpPort/simple-resource?fields=title", Seq("Accept-Language" → "ru")) shouldBe """{"title":"Привет"}"""
    httpGet(s"http://localhost:$httpPort/simple-resource?fields=title", Seq("Accept-Language" → "fr-CH, fr;q=0.9, en;q=0.8, de;q=0.7, *;q=0.5")) shouldBe """{"title":"Bonjour"}"""
    httpGet(s"http://localhost:$httpPort/simple-resource", Seq("Accept-Language" → "ru")) shouldBe """{"title~i18n":{"fr":"Bonjour","ru":"Привет"},"title":"Привет"}"""
    httpGet(s"http://localhost:$httpPort/simple-resource?fields=title~i18n", Seq("Accept-Language" → "ru")) shouldBe """{"title~i18n":{"fr":"Bonjour","ru":"Привет"}}"""
  }

  it should "serve resource with pattern" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service/{id}", Method.GET, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → command.request.headers.hrl.query.dynamic.id.toLong, "text_field" → "Yey")))
        })
        Continue
      }
    }

    httpGet(s"http://localhost:$httpPort/simple-resource/100500") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }

  it should "foward (eval)" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service/{id}", Method.GET, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → command.request.headers.hrl.query.dynamic.id.toLong, "text_field" → "Yey")))
        })
        Continue
      }
    }

    httpGet(s"http://localhost:$httpPort/simple-forward") shouldBe """{"text_field":"Yey","integer_field":100500}"""
  }

  it should "render link" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          val headers = MessageHeaders
            .builder
            .+=(Header.COUNT → Number(3))
            .withLink(Map("next_page_url" → HRL("hb://test-service/{id}", Obj.from("id" → "100500"))))
            .result()

          Ok(DynamicBody(Lst.from("a","b","c")), headers)
        })
        Continue
      }
    }

    val r = httpGetResponse(s"http://localhost:$httpPort/simple-resource")
    r.getResponseBody shouldBe """["a","b","c"]"""
    import scala.collection.JavaConverters._
    r.getHeaders("Link").asScala.head shouldBe "</simple-resource/100500?>; rel=next_page_url"
    r.getHeaders("X-Count").asScala.head shouldBe "3"
  }


  it should "render wrap collection" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          val headers = MessageHeaders
            .builder
            .+=(Header.COUNT → Number(3))
            .withLink(Map("next_page_url" → HRL("hb://test-service/{id}", Obj.from("id" → "100500"))))
            .result()

          Ok(DynamicBody(Lst.from("a","b","c")), headers)
        })
        Continue
      }
    }

    val r = httpGetResponse(s"http://localhost:$httpPort/simple-resource?wrap_collection=1")
    r.getResponseBody shouldBe """{"count":3,"link":{"next_page_url":"/simple-resource/100500"},"items":["a","b","c"]}"""
  }

  it should "substitute default query parameters" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service-with-default-query", Method.GET, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(DynamicBody(command.request.headers.hrl.query))
        })
        Continue
      }
    }

    httpGet(s"http://localhost:$httpPort/resource-with-query-string") shouldBe """{"test":"abc"}"""
    httpGet(s"http://localhost:$httpPort/resource-with-query-string?test=1") shouldBe """{"test":"1"}"""
  }

  it should "provide access to payload/raw body" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service-payload", Method.POST, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(request.request.body)
        })
        Continue
      }
    }

    httpPostUrlEncoded(s"http://localhost:$httpPort/test-post-payload", Map("integer_field" → List("100"), "text_field" → List("hello"))) shouldBe
      """{"text_field":"hello","integer_field":"100","payload":"integer_field=100&text_field=hello"}"""
  }
}
