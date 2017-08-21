package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.TestBase
import com.hypertino.facade.filter.model.{FieldFilterStage, FieldFilterStageRequest, FieldFilterStageResponse}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Forbidden, HRL, HeadersMap, Method, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.parser.HParser
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.Scheduler

import scala.util.Success

class FieldFilterSpec extends TestBase(ramlConfigFiles=Seq("raml-config-parser-test.raml")) {

  def fieldFilter(
                   aTypeDef: TypeDefinition,
                   aTypeDefinitions: Map[String, TypeDefinition],
                   query: Value = Null,
                   stage: FieldFilterStage = FieldFilterStageRequest
                 ) = new FieldFilterBase {
    override protected implicit def scheduler: Scheduler = FieldFilterSpec.this.scheduler
    def filter(body: Value): Task[Value] = {
      import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
      filterBody(body, RequestContext(DynamicRequest(HRL("hb://test", query), Method.GET, EmptyBody, headersMap=HeadersMap(
        FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
      ))), stage)
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

  def df(name: String) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        DenyAnnotation(predicate=None),
        name,
        "string"
      )
    )))
  }

  def ff(name: String, source: String, query: Map[String,String] = Map.empty, mode: String = "document", onError: String = FetchFieldFilter.ON_ERROR_FAIL, defaultValue: Option[String] = None) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        FetchAnnotation(predicate=None,
          location=PreparedExpression(source),
          query=query.map(kv ⇒ kv._1 → PreparedExpression(kv._2)),
          mode=mode,
          onError=onError,
          defaultValue=defaultValue.map(PreparedExpression(_))),
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
      Map.empty,
      Null,
      FieldFilterStageResponse
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
      ),
      Null,
      FieldFilterStageResponse
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

  it should "fetch field values" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("service_result" → "Yey")))
        })
        Continue
      }
    }

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service\""), isCollection = false ),
      Map.empty,
      Obj.from("fields" → "c"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → Obj.from("service_result" → "Yey"))

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service\""), isCollection = false ),
      Map.empty,
      Obj.from("fields" → "d"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc")
  }

  it should "deny if fields are set" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, df("y"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 2)))
      .runAsync
      .failed
      .futureValue shouldBe a[Forbidden[_]]
  }

  it should "not deny if fields are set" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, df("y"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1))
  }
}
