package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.modules.RamlConfigModule
import com.hypertino.facade.raml.{DataType, Field, SetAnnotation}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, HRL, HeadersMap, Method}
import com.hypertino.service.config.ConfigModule
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

class SetFieldRequestFilterTest extends FlatSpec with Matchers with ScalaFutures {
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))
  import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
  import monix.execution.Scheduler.Implicits.global
  val fullConfigPath = "./src/test/resources/inproc-test.conf"
  implicit val injector = new RamlConfigModule ::
    ConfigModule(configFiles = Seq(fullConfigPath), loadDefaults = true)

  injector.initNonLazy()

  "SetFieldRequestFilter" should "add fields" in {
    val filter = new SetFieldRequestFilter(
      Field("remote_address", DataType.DEFAULT_TYPE_NAME,
        Seq(SetAnnotation(predicate = None, source = PreparedExpression("remote_address")))
      ), DefaultExpressionEvaluator)

    val request = DynamicRequest(HRL("hb://test"), Method.POST, DynamicBody(Obj.from("id" → "100500")), headersMap=HeadersMap(
      FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
    ))
    val requestContext = RequestContext(request)
    val result = filter.apply(requestContext).futureValue
    result.request.body.content shouldBe Obj.from("id" → "100500", "remote_address" → "127.0.0.1")
  }

  "SetFieldRequestFilter" should "override existing fields" in {
    val filter = new SetFieldRequestFilter(
      Field("remote_address", DataType.DEFAULT_TYPE_NAME,
        Seq(SetAnnotation(predicate = None, source = PreparedExpression("remote_address")))
      ), DefaultExpressionEvaluator)

    val request = DynamicRequest(HRL("hb://test"), Method.POST,
      DynamicBody(Obj.from("id" → "100500", "remote_address" → "10.0.0.1")), headersMap=HeadersMap(
      FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
    ))
    val requestContext = RequestContext(request)
    val result = filter.apply(requestContext).futureValue
    result.request.body.content shouldBe Obj.from("id" → "100500", "remote_address" → "127.0.0.1")
  }

  "SetFieldRequestFilter" should "set null corresponding to the source" in {
    val filter = new SetFieldRequestFilter(
      Field("some_field", DataType.DEFAULT_TYPE_NAME,
        Seq(SetAnnotation(predicate = None, source = PreparedExpression("headers.not-existing")))
      ), DefaultExpressionEvaluator)

    val request = DynamicRequest(HRL("hb://test"), Method.POST, DynamicBody(Obj.from(
      "id" → "100500"
    )), headersMap=HeadersMap(
      FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
    ))

    val requestContext = RequestContext(request)
    val result = filter.apply(requestContext).futureValue
    result.request.body.content shouldBe Obj.from("id" → "100500", "some_field" → Null)
  }
}