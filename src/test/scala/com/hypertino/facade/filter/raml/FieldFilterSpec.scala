package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Obj, Value}
import com.hypertino.facade.filter.model.FieldFilter
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.Field
import com.hypertino.hyperbus.model.{DynamicRequest, EmptyBody, HRL, Method}
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

class FieldFilterSpec extends FlatSpec with Matchers with ScalaFutures {
  private implicit val scheduler = Scheduler.Implicits.global
  def fieldFilter(aFields: Seq[FieldWithFilter]) = new FieldFilterBase {
    override protected def fields: Seq[FieldWithFilter] = aFields
    override protected implicit def scheduler: Scheduler = FieldFilterSpec.this.scheduler
    def filter(body: Value): Task[Value] = {
      import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
      filterBody(body, RequestContext(DynamicRequest(HRL("hb://test"), Method.GET, EmptyBody)))
    }
  }

  def rf(name: String) = FieldWithFilter(Field(name, "test", null),RemoveFieldFilter)
  def sf(name: String, v: Value) = FieldWithFilter(Field(name, "test", null), new FieldFilter{
    override def apply(rootValue: Value, field: Field, value: Option[Value], requestContext: RequestContext): Task[Option[Value]] = Task {
      Some(v)
    }
  })

  "FieldFilterBase" should "leave body as-is if no filter on fields are defined" in {
    fieldFilter(Seq.empty).filter(Obj.from("a" → 100500))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500)
  }

  it should "remove values" in {
    fieldFilter(Seq(rf("b")))
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500)
  }

  it should "remove inner values" in {
    fieldFilter(Seq(rf("b.y"), rf("c.z.z")))
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2), "c" → Obj.from("z" → Obj.from("x" → 4, "z" → 5))))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1), "c" → Obj.from("z" → Obj.from("x" → 4)))
  }

  it should "add values" in {
    fieldFilter(Seq(sf("c", "Yey")))
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → "Yey")
  }

  it should "set values" in {
    fieldFilter(Seq(sf("b", "Yey")))
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "Yey")
  }

  it should "add inner values" in {
    fieldFilter(Seq(sf("b.z", "Yey")))
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2, "z" → "Yey"))
  }

  it should "set inner values" in {
    fieldFilter(Seq(sf("b.y", "Yey"), sf("c.y.z", 123)))
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → "Yey"), "c" → Obj.from("y" → Obj.from("z" → 123)))
  }

  it should "remove values inside collections" in {
    fieldFilter(Seq(rf("`[]`.b")))
      .filter(Lst.from(Obj.from("a" → 100500, "b" → "abc")))
      .runAsync
      .futureValue shouldBe Lst.from(Obj.from("a" → 100500))
  }
}
