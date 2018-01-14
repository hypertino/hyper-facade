/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Null, Obj, Value}
import com.hypertino.facade.{DummyMetricsTracker, TestBaseWithHyperbus}
import com.hypertino.facade.filter.model.{FieldFilterStage, FieldFilterStageEvent, FieldFilterStageRequest, FieldFilterStageResponse}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.filters.annotated._
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.raml._
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicRequestObservableMeta, EmptyBody, Forbidden, HRL, Headers, Method, Ok}
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.metrics.MetricsTracker
import com.hypertino.parser.HParser
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import monix.execution.atomic.AtomicInt
import scaldi.DynamicModule

import scala.util.Success

class FieldFilterSpec extends TestBaseWithHyperbus(ramlConfigFiles=Seq("raml-config-parser-test.raml")) {
  private var dataTypes = Map.empty[String, TypeDefinition]

  override def extraModule = DynamicModule({ module =>
    module.bind [RamlConfiguration] toProvider raml
  })

  def raml: RamlConfiguration = {
    if (testServices == null) {
      RamlConfiguration("", Map.empty, dataTypes)
    }
    else {
      import testServices._

      if (dataTypes == null)
        originalRamlConfig
      else
        RamlConfiguration(originalRamlConfig.baseUri,
          originalRamlConfig.resourcesByPattern,
          originalRamlConfig.dataTypes ++ dataTypes)
    }
  }

  import testServices._

  def fieldFilter(
                   aTypeDef: TypeDefinition,
                   aTypeDefinitions: Map[String, TypeDefinition],
                   query: Value = Null,
                   stage: FieldFilterStage = FieldFilterStageRequest
                 ) = new FieldFilterBase {

    FieldFilterSpec.this.dataTypes = aTypeDefinitions

    override protected implicit def scheduler: Scheduler = FieldFilterSpec.this.scheduler
    def filter(body: Value): Task[Value] = {
      import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
      filterBody(body, RequestContext(DynamicRequest(HRL("hb://test", query), Method.GET, EmptyBody, headers=Headers(
        FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
      )), None), stage)
    }
    override protected def typeDef: TypeDefinition = aTypeDef
    //override protected def typeDefinitions: Map[String, TypeDefinition] = aTypeDefinitions
    override protected def expressionEvaluator: ExpressionEvaluator = DefaultExpressionEvaluator
    override protected def metricsTracker: MetricsTracker = DummyMetricsTracker
  }

  def tt(args: (String,String)*): Map[String, Field] = {
    args.map { case (k,t) ⇒
      k -> Field(k, t, Seq.empty, None)
    } toMap
  }

  def rf(name: String, stages: Set[FieldFilterStage] = Set(FieldFilterStageResponse,FieldFilterStageEvent)) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        RemoveFieldAnnotation(predicate=None,stages=stages),
        name,
        "string"
      )
    ), None))
  }

  def df(name: String, stages: Set[FieldFilterStage] = Set(FieldFilterStageRequest)) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        ErrorResponseFieldAnnotation(predicate=None,stages=stages,_name="forbidden"),
        name,
        "string"
      )
    ), None))
  }

  def ff(name: String,
         source: String,
         query: Map[String,String] = Map.empty,
         expects: String = "document",
         onError: String = FetchFilter.ON_ERROR_DEFAULT,
         defaultValue: Map[String, String] = Map.empty,
         stages: Set[FieldFilterStage] = Set(FieldFilterStageResponse,FieldFilterStageEvent),
         selector: Option[String] = None,
         always: Boolean=false,
         iterateOn: Option[String] = None
        ) = {
    Map(name → Field(name, "string", Seq(
      new FieldAnnotationWithFilter(
        FetchFieldAnnotation(predicate=None,
          location=PreparedExpression(source),
          query=query.map(kv ⇒ kv._1 → PreparedExpression(kv._2)),
          expects=expects,
          onError=onError,
          selector=selector.map(PreparedExpression(_)),
          default=defaultValue.map(kv => kv._1 -> PreparedExpression(kv._2)),
          stages=stages,always=always,
          iterateOn=iterateOn.map(PreparedExpression(_))),
        name,
        "string"
      )
    ), None))
  }

  def sf(name: String, expression: String = "1", t:String = "string", stages: Set[FieldFilterStage] = Set(FieldFilterStageRequest)) = {
    val e = HParser(expression)
    val pp = PreparedExpression(expression,e)

    Map(name → Field(name, t, Seq(
      new FieldAnnotationWithFilter(
        SetFieldAnnotation(predicate=None,source=pp,stages=stages),
        name,
        t
      )
    ), None))
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

  it should "set inner values, if target instance is collection added by other filter" in {
    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, sf("y", "[{\"k\":\"Yey\"}]", "T3[]"), isCollection = false ),
        "T3" → TypeDefinition("T3", None, Seq.empty, sf("l", "123"), isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → Lst.from(Obj.from("k" → "Yey", "l" → 123))))
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

  it should "fetch field values and respect fields=specific" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service1", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("service_result" → "Yey")))
        })
        Continue
      }
    }

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service1\""), isCollection = false ),
      Map.empty,
      Obj.from("fields" → "c"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → Obj.from("service_result" → "Yey"))

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service1\""), isCollection = false ),
      Map.empty,
      Obj.from("fields" → "d"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc")

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service1\"", selector=Some("source.service_result")), isCollection = false),
      Map.empty,
      Obj.from("fields" → "c"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → "Yey")
  }

  it should "not fetch field values if they aren't specified in filter" in {
    @volatile var fetched = false
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service2", Method.GET, None))
      ).subscribe { implicit request =>
        fetched = true
        request.reply(Success {
          Ok(DynamicBody(Obj.from("service_result" → "Yey")))
        })
        Continue
      }
    }

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service2\""), isCollection = false ),
      Map.empty,
      Null,
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc")

    fetched shouldBe false
  }

  it should "fetch field values and respect fields=**" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service3", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("service_result" → "Yey")))
        })
        Continue
      }
    }

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("c", "\"hb://test-service3\""), isCollection = false ),
      Map.empty,
      Obj.from("fields" → "**"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc"))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc", "c" → Obj.from("service_result" → "Yey"))
  }

  it should "iterate and fetch field values" in {
    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://test-service4", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("c" → 55)))
        })
        Continue
      }
    }

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, ff("r", "\"hb://test-service4\"", iterateOn=Some("this.src")), isCollection = false),
      Map.empty,
      Obj.from("fields" → "r"),
      FieldFilterStageResponse
    )
      .filter(Obj.from("a" → 100500, "b" → "abc", "src" -> Obj.from("x" -> 1, "y" -> 2, "z" -> 3)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → "abc",
        "src" -> Obj.from("x" -> 1, "y" -> 2, "z" -> 3),
        "r" → Obj.from("x" -> Obj.from("c" -> 55), "y" -> Obj.from("c" -> 55), "z" -> Obj.from("c" -> 55))
      )
  }

  it should "forbidden if fields are set" in {
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

  it should "not forbidden if fields are not set" in {
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

  it should "apply multiple filters" in {
    val forbidden = df("y")("y")
    val set = sf("y", "123")("y")
    val yf = Map("y" → forbidden.copy(annotations=forbidden.annotations ++ set.annotations))

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, yf, isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1)))
      .runAsync
      .futureValue shouldBe Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 123))

    fieldFilter(
      TypeDefinition("T1", None, Seq.empty, tt("b" → "T2"), isCollection = false ),
      Map(
        "T2" → TypeDefinition("T2", None, Seq.empty, yf, isCollection = false )
      )
    )
      .filter(Obj.from("a" → 100500, "b" → Obj.from("x" → 1, "y" → 1)))
      .runAsync
      .failed
      .futureValue shouldBe a[Forbidden[_]]
  }
}
