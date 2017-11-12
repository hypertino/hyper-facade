/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.HRL
import org.scalatest.{FlatSpec, Matchers}

class HrlTransformerSpec extends FlatSpec with Matchers {
  "HrlTransformerSpec" should "rewrite backward encoded source HRL with inner pattern params" in {
    val hrl = HRL("hb://test/abc%2F100500", Obj.from("q" → "100"))
    val sourcePattern = HRL("hb://test/{path}", Obj.from("path" → "abc/{id}"))
    val destPattern = HRL("/test/{id}")

    HrlTransformer.rewriteBackWithPatterns(hrl, sourcePattern, destPattern) shouldBe HRL(
      "/test/{id}", Obj.from("id" → "100500", "q" → "100")
    )
  }

  it should "rewrite forward encoded source HRL with inner pattern params" in {
    val hrl = HRL("/test/100500", Obj.from("q" → "100"))
    val sourcePattern = HRL("/test/{id}")
    val destPattern = HRL("hb://test/{path}", Obj.from("path" → "abc/{id}"))

    HrlTransformer.rewriteForwardWithPatterns(hrl, sourcePattern, destPattern) shouldBe HRL(
      "hb://test/{path}", Obj.from("path" → "abc/100500", "q" → "100")
    )
  }

  it should "rewrite forward encoded source HRL without inner pattern params" in {
    val hrl = HRL("/test/100500", Obj.from("q" → "100"))
    val sourcePattern = HRL("/test/{id}")
    val destPattern = HRL("hb://test/{id}")

    HrlTransformer.rewriteForwardWithPatterns(hrl, sourcePattern, destPattern) shouldBe HRL(
      "hb://test/{id}", Obj.from("id" → "100500", "q" → "100")
    )
  }

  it should "rewrite and append query parameters" in {
    val hrl = HRL("/test/100500", Obj.from("q" → "100", "x" -> "1"))
    val sourcePattern = HRL("/test/{id}")
    val destPattern = HRL("hb://test/{id}", Obj.from("id" -> "{id}", "x" -> "2"))

    HrlTransformer.rewriteForwardWithPatterns(hrl, sourcePattern, destPattern) shouldBe HRL(
      "hb://test/{id}", Obj.from("id" → "100500", "q" → "100", "x" -> "2")
    )
  }
}

