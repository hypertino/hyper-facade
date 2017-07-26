package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Null, Obj}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, PreparedExpression}
import com.hypertino.facade.model.{FacadeHeaders, RequestContext}
import com.hypertino.facade.modules.RamlConfigModule
import com.hypertino.facade.raml.{DataType, Field, PrivateAnnotation, SetAnnotation}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, HRL, HeadersMap, Method, Ok}
import com.hypertino.service.config.ConfigModule
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}

class PrivateFilterTest extends FlatSpec with Matchers with ScalaFutures {
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))
  import com.hypertino.hyperbus.model.MessagingContext.Implicits.emptyContext
  import monix.execution.Scheduler.Implicits.global
  val fullConfigPath = "./src/test/resources/inproc-test.conf"
  implicit val injector = new RamlConfigModule ::
    ConfigModule(configFiles = Seq(fullConfigPath), loadDefaults = true)

  injector.initNonLazy()

  "PrivateResponseFilter" should "delete designated field" in {
    val f1 = new PrivateResponseFilter(
      Field("password", DataType.DEFAULT_TYPE_NAME,
        Seq(PrivateAnnotation(predicate = None))
      ), DefaultExpressionEvaluator)

    val f2 = new PrivateResponseFilter(
      Field("key.private_key", DataType.DEFAULT_TYPE_NAME,
        Seq(PrivateAnnotation(predicate = None))
      ), DefaultExpressionEvaluator)

    val request = DynamicRequest(HRL("hb://test"), Method.POST, DynamicBody(Obj.from("id" → "100500")), headersMap=HeadersMap(
      FacadeHeaders.REMOTE_ADDRESS → "127.0.0.1"
    ))
    val requestContext = RequestContext(request)
    val response = Ok(DynamicBody(Obj.from("user_id" → "100500", "password" → "my-secret", "key" → Obj.from(
      "public_key" → "abc",
      "private_key" → "def"
    ))))

    val r1 = f1.apply(requestContext, response).futureValue
    r1.body.content shouldBe Obj.from("user_id" → "100500", "key" → Obj.from(
      "public_key" → "abc",
      "private_key" → "def"
    ))

    val r2 = f2.apply(requestContext, r1).futureValue
    r2.body.content shouldBe Obj.from("user_id" → "100500", "key" → Obj.from(
      "public_key" → "abc"
    ))
  }
}