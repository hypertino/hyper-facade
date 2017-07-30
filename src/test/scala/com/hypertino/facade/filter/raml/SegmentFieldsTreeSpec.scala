package com.hypertino.facade.filter.raml

import com.hypertino.facade.raml.Field
import org.scalatest.{FlatSpec, Matchers}

class SegmentFieldsTreeSpec extends FlatSpec with Matchers{
  "SegmentFieldsTree" should "make a tree from flat list" in {
    val l = Seq(
      ff("a"),
      ff("b.c"),
      ff("c.c"),
      ff("b.d"),
      ff("c")
    )

    val tree = SegmentFieldsTree(l)
    tree shouldBe SegmentFieldsTree(
      Map("a" → ff("a"), "c" → ff("c")),
      Map(
        "b" → SegmentFieldsTree(
          Map("c" → ff("b.c"), "d" → ff("b.d")),
          Map.empty
        ),
        "c" → SegmentFieldsTree(
          Map("c" → ff("c.c")),
          Map.empty
        )
      )
    )
  }

  "SegmentFieldsTree" should "make a tree with one element from flat list" in {
    val l = Seq(
      ff("a")
    )

    val tree = SegmentFieldsTree(l)
    tree shouldBe SegmentFieldsTree(
      Map("a" → ff("a")),
      Map.empty
    )
  }

  def ff(name: String) = FieldWithFilter(Field(name, "string", Seq.empty), null)
}




