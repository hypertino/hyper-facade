/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.integration

import com.hypertino.binders.value.{Lst, Number, Obj}
import com.hypertino.facade.TestBaseWithFacade
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, HRL, Header, MessageHeaders, Method, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.util.Success

class HttpPostFormTest extends TestBaseWithFacade("inproc-test.conf") {
  "Facade" should "convert application/x-www-form-urlencoded to json message" in {
    val t = testObjects
    import t._
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service-www-form-urlencoded", Method.POST, None))
      ).subscribe { implicit command =>
        command.reply(Success {
          Ok(command.request.body)
        })
        Continue
      }
    }

    // todo: why the json is inverted?
    httpPostUrlEncoded(s"http://localhost:$httpPort/test-post-www-form", Map("a" → List("100"), "b" → List("hello"))) shouldBe
      """{"b":"hello","a":"100"}"""
  }
}
