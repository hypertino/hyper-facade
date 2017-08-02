package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Obj, Value}
import com.hypertino.facade.TestBase
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.{DynamicRequest, EmptyBody, HRL, HeadersMap, Method}
import com.hypertino.parser.HParser
import monix.eval.Task
import monix.execution.Scheduler

class FieldFilterSpec extends TestBase(ramlConfigFiles=Seq("raml-config-parser-test.raml")) {

  def fieldFilter(aTypeDef: TypeDefinition, aTypeDefinitions: Map[String, TypeDefinition]) = new FieldFilterBase {
    override protected implicit def scheduler: Scheduler = FieldFilterSpec.this.scheduler
    def filter(body: Value): Task[Value] = {
      import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
      filterBody(body, RequestContext(DynamicRequest(HRL("hb://test"), Method.GET, EmptyBody, headersMap=HeadersMap(
        FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
      ))))
    }
    override protected def typeDef: TypeDefinition = aTypeDef
    override protected def typeDefinitions: Map[String, TypeDefinition] = aTypeDefinitions
    override protected def expressionEvaluator: ExpressionEvaluator = DefaultExpressionEvaluator
  }

  def tt(args: (String,String)*): Map[String, Field] = {
    args.map { case (k,t) ⇒
      k -> Field(k, t, Seq.empty)
    } toMap
  }

  def rf(name: String) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        RemoveAnnotation(predicate=None),
        name,
        "string"
      )
    )))
  }

  def sf(name: String, expression: String = "1") = {
    val e = HParser(expression)
    val pp = PreparedExpression(expression,e)

    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        SetAnnotation(predicate=None,source=pp),
        name,
        "string"
      )
    )))
  }

  "FieldFilterBase" should "leave body as-is if no filter on fields are defined" in {
    fieldFilter(
      TypeDefinition("test", None, Seq.empty, Map.empty, isCollection = false ),
      Map.empty
    )
      .filter(Obj.from("a" → 100500))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500)
  }

  it should "remove values" in {
    fieldFilter(
      TypeDefinition("test", None, Seq.empty, rf("b"), isCollection = false ),
      Map.empty
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500)
  }

  it should "remove inner values" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2", "c" → "T3"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, rf("y"), isCollection = false ),
        "T3" → TypeDefinition("T3", None, Seq.empty, tt("z" → "T4"), isCollection = false ),
        "T4" → TypeDefinition("T4", None, Seq.empty, rf("z"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2), "c" → Obj.from("z" → Obj.from("x" → 4, "z" → 5))))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1), "c" → Obj.from("z" → Obj.from("x" → 4)))
  }

  it should "add values" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, sf("c", "\"Yey\""), isCollection = false ),
      Map.empty
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → "Yey")
  }

  it should "set values" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, sf("b", "\"Yey\""), isCollection = false ),
      Map.empty
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "Yey")
  }

  it should "add inner values" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, sf("z", "\"Yey\""), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2, "z" → "Yey"))
  }

  it should "add inner values with root/this expression" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, sf("z", "this.y") ++ sf("z2", "root.a"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2, "z" → 2, "z2" → 100500))
  }

  it should "set inner values, but only if target type instance exists" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2", "c" → "T3"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, sf("y", "\"Yey\""), isCollection = false ),
        "T3" → TypeDefinition("T3", None, Seq.empty, tt("y" → "T4"), isCollection = false ),
        "T4" → TypeDefinition("T4", None, Seq.empty, sf("z", "123"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → "Yey"))
  }

  it should "add values into the collection" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, sf("c", "\"Yey\""), isCollection = true ),
      Map.empty
    )
      .filter(Lst.from(Obj.from("a" → 100500, "b" → "abc"), Obj.from("a" → 100501, "b" → "abd")))
      .runAsync
      .futureValue shouldBe Lst.from(Obj.from("a" → 100500, "b" → "abc", "c" → "Yey"), Obj.from("a" → 100501, "b" → "abd", "c" → "Yey"))
  }

  it should "add values into the inner collections" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, sf("c", "\"Yey\"") ++ tt("d" → "T2"), isCollection = true ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, sf("x", "\"XXX\""), isCollection = true )
      )
    )
      .filter(Lst.from(Obj.from("a" → 100500, "b" → "abc", "d" → Lst.from(Obj.from("z" → 1))), Obj.from("a" → 100501, "b" → "abd")))
      .runAsync
      .futureValue shouldBe Lst.from(
        Obj.from("a" → 100500, "b" → "abc", "c" → "Yey", "d" → Lst.from(Obj.from("z" →1, "x" → "XXX"))),
        Obj.from("a" → 100501, "b" → "abd", "c" → "Yey")
      )
  }
}
