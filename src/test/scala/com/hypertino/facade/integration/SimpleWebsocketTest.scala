package com.hypertino.facade.integration

import com.hypertino.binders.value.Obj
import com.hypertino.facade.{TestBase, TestBaseWithFacade, TestPortGenerator}
import com.hypertino.facade.workers.TestQueue
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api.PublishResult
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import monix.execution.Ack.Continue

import scala.util.Success

class SimpleWebsocketTest extends TestBaseWithFacade("inproc-test.conf") {
  "Facade" should "serve simple event feed" in {
    val t = testObjects
    import t._

    val q = new TestQueue
    val client = createWsClient("unreliable-feed-client", "/", q.put,port=t.httpPort)
    import MessagingContext.Implicits.emptyContext

    register {
      hyperbus.commands[DynamicRequest](
        DynamicRequest.requestMeta,
        DynamicRequestObservableMeta(RequestMatcher("hb://ws-test-service/unreliable-feed", Method.GET, None))
      ).subscribe { implicit request =>
        request.reply(Success {
          Ok(DynamicBody(Obj.from("integer_field" → 100500, "text_field" → "Yey")))
        })
        Continue
      }
    }

    client ! DynamicRequest(HRL("/simple-resource-with-events/unreliable-feed"), "subscribe",
      EmptyBody,
      MessageHeaders.builder
        .withContentType(Some("application/vnd.feed-test+json"))
        .withMessageId("100500")
        .withCorrelation("abc100500")
        .result()
    )

    val resourceState = q.nextResponse().futureValue
    resourceState shouldBe a[Ok[_]]
    resourceState.body.content shouldBe Obj.from("integer_field" → 100500, "text_field" → "Yey")

    hyperbus.publish(DynamicRequest(HRL("hb://ws-test-service/unreliable-feed"), Method.FEED_POST,
      DynamicBody(Obj.from("integer_field" → 54321, "text_field" → "Bye")),
      MessageHeaders.builder
        .withContentType(Some("application/vnd.feed-test+json"))
        .withMessageId("100500")
        .withCorrelation("abc100500")
        .result()
    )).runAsync.futureValue shouldBe a[Seq[_]]

    val event1 = q.nextEvent().futureValue
    event1.headers.method shouldBe Method.FEED_POST
    event1.body.content shouldBe Obj.from("integer_field" → 54321, "text_field" → "Bye")

    client ! DynamicRequest(HRL("/simple-resource-with-events/unreliable-feed"), "unsubscribe",
      EmptyBody,
      MessageHeaders.builder
        .withContentType(Some("application/vnd.feed-test+json"))
        .withMessageId("100500")
        .withCorrelation("abc100500")
        .result()
    )
  }

  //    "handle error response" in {
  //      val q = new TestQueue
  //      val client = createWsClient("error-feed-client", "/v3/upgrade", q.put)
  //
  //      register {
  //        testService.onCommand(RequestMatcher(Some(Uri("/500-resource")), Map(Header.METHOD → Specific(Method.GET))),
  //          com.hypertino.hyperbus.model.InternalServerError(ErrorBody("unhandled-exception", Some("Internal server error"), errorId = "123"))
  //        ).futureValue
  //      }
  //
  //      client ! FacadeRequest(Uri("/v3/500-resource"), "subscribe",
  //        Map(Header.CONTENT_TYPE → Seq("application/vnd.feed-test+json"),
  //          FacadeHeaders.CLIENT_MESSAGE_ID → Seq("messageId"),
  //          FacadeHeaders.CLIENT_CORRELATION_ID → Seq("correlationId")),
  //        Obj(Map("content" → Text("haha"))))
  //
  //      val resourceState = q.next().futureValue
  //      resourceState should startWith ("""{"status":500,"headers":""")
  //      resourceState should include (""""code":"unhandled-exception"""")
  //    }
}