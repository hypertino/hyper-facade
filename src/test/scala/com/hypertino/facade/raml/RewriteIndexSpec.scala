package com.hypertino.facade.raml

import com.hypertino.binders.value.Obj
import com.hypertino.facade.utils.HrlTransformer
import com.hypertino.facade.{CleanRewriteIndex, TestBase}
import com.hypertino.hyperbus.model
import com.hypertino.hyperbus.model.HRL
import org.scalatest.{FlatSpec, Matchers}

class RewriteIndexSpec extends TestBase() with CleanRewriteIndex with Matchers {
  "RewriteIndex" should "rewriteLinkToOriginal" in {
    HrlTransformer.rewriteLinkToOriginal(HRL("hb://test-service"), 3) shouldBe HRL("/simple-resource")
  }

  it should "rewriteBackward" in {
    HrlTransformer.rewriteBackward(HRL("hb://test-service"), model.Method.GET) shouldBe HRL("/simple-resource")
  }

  it should "rewriteBackward with patterns" in {
    HrlTransformer.rewriteBackward(HRL("hb://test-service/{id}", Obj.from("id" → 100500)), model.Method.GET) shouldBe HRL(
      "/simple-resource/{id}", Obj.from("id" → 100500)
    )
  }

  it should "rewriteBackward with patterns and query" in {
    HrlTransformer.rewriteBackward(HRL("hb://test-service-with-args/{path}", Obj.from("path" → "abc/100500", "extra" → "Yey")), model.Method.GET) shouldBe HRL(
      "/resource-with-rewrite-args/{some_id}", Obj.from("some_id" → "100500", "extra" → "Yey")
    )
  }
}
