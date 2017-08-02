package com.hypertino.facade.utils

import org.scalatest.{FlatSpec, Matchers}

class SelectFieldsSpec extends FlatSpec with Matchers{
  "SelectFields" should "parse single fields" in {
    SelectFields("a") shouldBe Map("a" → SelectField("a", Map.empty))
  }

  "SelectFields" should "parse multiple fields" in {
    SelectFields("a,b,c") shouldBe Map(
      "a" → SelectField("a", Map.empty),
      "b" → SelectField("b", Map.empty),
      "c" → SelectField("c", Map.empty)
    )
  }

  "SelectFields" should "parse inner fields" in {
    SelectFields("a.x") shouldBe Map(
      "a" → SelectField("a", Map("x" →
        SelectField("x", Map.empty)
      ))
    )

    SelectFields("a.{x,y}") shouldBe Map(
      "a" → SelectField("a", Map(
        "x" → SelectField("x", Map.empty),
        "y" → SelectField("y", Map.empty)
      ))
    )

    SelectFields("a.{x.{k,m},y}") shouldBe Map(
      "a" → SelectField("a", Map(
        "x" → SelectField("x",
          Map(
            "k" → SelectField("k", Map.empty),
            "m" → SelectField("m", Map.empty)
          )
        ),
        "y" → SelectField("y", Map.empty)
      ))
    )
  }
}
