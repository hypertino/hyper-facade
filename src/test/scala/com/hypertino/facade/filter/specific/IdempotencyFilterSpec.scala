/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.specific

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.facade.TestBaseWithHyperbus
import com.hypertino.facade.apiref.idempotency._
import com.hypertino.facade.filter.parser.DefaultExpressionEvaluator
import com.hypertino.facade.filters.chain.after_reply.IdempotencyResponseFilter
import com.hypertino.facade.filters.chain.before_resolved.IdempotencyRequestFilter
import com.hypertino.facade.model.{FilterInterruptException, RequestContext}
import com.hypertino.hyperbus.model.{Created, DynamicBody, DynamicRequest, EmptyBody, ErrorBody, HRL, Headers, MessagingContext, Method, NotFound, Ok, PreconditionFailed, RequestBase, ResponseBase}
import com.hypertino.hyperbus.subscribe.Subscribable
import monix.eval.Task

import scala.collection.mutable

class IdempotencyFilterSpec extends TestBaseWithHyperbus("inproc-test.conf") with Subscribable {
  Thread.sleep(500)

  import testServices._
  hyperbus.subscribe(this)
  private val isRequests = mutable.MutableList[RequestBase]()

  override def beforeEach(): Unit = {
    isRequests.clear()
    super.beforeEach()
  }

  def onIdempotentRequestPut(implicit put: IdempotentRequestPut): Task[ResponseBase] = {
    isRequests += put
    if (put.uri.endsWith("unprocessed")) {
      Task.now(Ok(EmptyBody))
    }
    else {
      Task.now(PreconditionFailed(ErrorBody("precondition-failed-test")))
    }
  }

  def onIdempotentResponseGet(implicit get: IdempotentResponseGet): Task[Ok[ResponseWrapper]] = {
    isRequests += get
    if (get.uri.endsWith("processed")) {
      val response = Ok(DynamicBody(Obj.from("user" → "Kate")))
      Task.now(Ok(ResponseWrapper(Obj(response.headers.underlying), response.body.content)))
    }
    else {
      Task.raiseError(NotFound(ErrorBody("not-found")))
    }
  }

  def onIdempotentResponsePut(implicit put: IdempotentResponsePut): Task[ResponseBase] = {
    isRequests += put
    Task.now(Created(EmptyBody))
  }

  "IdempotencyRequestFilter" should "lock resource and set context if key isn't yet processed" in {
    val filter = new IdempotencyRequestFilter(hyperbus, DefaultExpressionEvaluator, scheduler)
    implicit val mcx = MessagingContext.Implicits.emptyContext
    val rc = RequestContext(
      DynamicRequest(HRL("hb://unprocessed"), Method.GET, EmptyBody, Headers(
        "Idempotency-Key" → "ik_100500"
      ))
    )
    val result = filter.apply(rc).runAsync.futureValue
    result.contextStorage.dynamic.idempotent_request shouldBe Obj.from("key" → "ik_100500", "uri" → "hb://unprocessed")
    isRequests.headOption.foreach(_ shouldBe a [IdempotentRequestPut])
    val p = isRequests.head.asInstanceOf[IdempotentRequestPut]
    p.uri shouldBe "hb://unprocessed"
    p.key shouldBe "ik_100500"
  }

  it should "answer with response if key is already processed" in {
    val filter = new IdempotencyRequestFilter(hyperbus, DefaultExpressionEvaluator, scheduler)
    implicit val mcx = MessagingContext.Implicits.emptyContext
    val rc = RequestContext(
      DynamicRequest(HRL("hb://processed"), Method.GET, EmptyBody, Headers(
        "Idempotency-Key" → "ik_100500"
      ))
    )
    val result = filter
      .apply(rc)
      .runAsync
      .failed
      .futureValue

    result shouldBe a[FilterInterruptException]
    val response = result.asInstanceOf[FilterInterruptException].response
    response shouldBe a[Ok[_]]
    response.body shouldBe DynamicBody(Obj.from("user" → "Kate"))

    isRequests.headOption.foreach(_ shouldBe a [IdempotentRequestPut])
    isRequests.tail.headOption.foreach(_ shouldBe a [IdempotentResponseGet])
    val r = isRequests.tail.head.asInstanceOf[IdempotentResponseGet]
    r.uri shouldBe "hb://processed"
    r.key shouldBe "ik_100500"
  }

  "IdempotencyResponseFilter" should "save resource if idempotent_request is set in context" in {
    val filter = new IdempotencyResponseFilter(hyperbus, DefaultExpressionEvaluator, scheduler)
    implicit val mcx = MessagingContext.Implicits.emptyContext
    val rc = RequestContext(
      DynamicRequest(HRL("hb://processed"), Method.GET, EmptyBody, Headers(
        "Idempotency-Key" → "ik_100500"
      ))).copy(
        contextStorage=Obj.from("idempotent_request" → Obj.from("key" → "ik_100500", "uri" → "hb://processed"))
      )
    val response = Ok(DynamicBody(Obj.from("user" → "John")))
    val result = filter.apply(rc, response).runAsync.futureValue
    result shouldBe response
    val r = isRequests.head.asInstanceOf[IdempotentResponsePut]
    r.key shouldBe "ik_100500"
    r.uri shouldBe "hb://processed"
    r.body shouldBe ResponseWrapper(Obj(response.headers.underlying),response.body.content)
  }
}
