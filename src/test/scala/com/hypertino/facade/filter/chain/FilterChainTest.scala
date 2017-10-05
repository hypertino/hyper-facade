package com.hypertino.facade.filter.chain

import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.filter.model.{RequestFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator}
import com.hypertino.facade.model.{FilterInterruptException, _}
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, EmptyBody, ErrorBody, Forbidden, HRL, Method, Ok, _}
import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

class FilterChainTest extends FreeSpec with Matchers with ScalaFutures {

  val filterChain = SimpleFilterChain(
    requestFilters = Seq(new TestRequestFilter),
    responseFilters = Seq(new TestResponseFilter)
  ) // todo: + test eventFilters

  class TestRequestFilter extends RequestFilter {
    override protected def expressionEvaluator: ExpressionEvaluator = DefaultExpressionEvaluator
    override def  apply(requestContext: RequestContext)
                       (implicit scheduler: Scheduler): Task[RequestContext] = {
      if (requestContext.request.headers.hrl.location != "/interrupted") {
        Task.now(requestContext)
      }
      else {
        implicit val mcx = requestContext.request
        Task.raiseError(Forbidden(ErrorBody("Forbidden")))
      }
    }
  }

  class TestResponseFilter extends ResponseFilter {
    override protected def expressionEvaluator: ExpressionEvaluator = DefaultExpressionEvaluator
    override def apply(requestContext: RequestContext, output: DynamicResponse)
                      (implicit scheduler: Scheduler): Task[DynamicResponse] = {
      if (requestContext.request.headers.hrl.location != "/interrupted") {
        Task.now(output)
      }
      else {
        implicit val mcx = requestContext.request
        Task.raiseError(new FilterInterruptException(
          response = Ok(EmptyBody, MessageHeaders.builder.+=("x-http-header" → "Accept-Language").result()),
          message = "Interrupted by filter"
        ))
      }
    }
  }

  import MessagingContext.Implicits.emptyContext
  import monix.execution.Scheduler.Implicits.global

  "FilterChain " - {
    "request filters with interruption" in {
      val request = DynamicRequest(HRL("/interrupted"), Method.GET, DynamicBody(Text("test body")))

      filterChain.filterRequest(RequestContext(request))
        .runAsync
        .failed
        .futureValue shouldBe a[Forbidden[_]]
    }

    "request filters" in {
      val request = DynamicRequest(HRL("/successfull"), Method.GET, DynamicBody(Text("test body")))

      val filteredRequest = filterChain.filterRequest(RequestContext(request)).runAsync.futureValue.request

      filteredRequest.body shouldBe DynamicBody(Text("test body"))
      filteredRequest.headers.hrl shouldBe HRL("/successfull")
      filteredRequest.headers.method shouldBe Method.GET
    }

    "response filters with interruption" in {
      val request = DynamicRequest(HRL("/interrupted"), Method.GET, DynamicBody(Text("test body")))
      val response = Created(DynamicBody("response body"))

      val interrupt = filterChain.filterResponse(RequestContext(request), response)
        .runAsync
        .failed
        .futureValue

      interrupt shouldBe a[FilterInterruptException]
      val r = interrupt.asInstanceOf[FilterInterruptException].response
      r.body.content shouldBe Null
      r.headers should contain("x-http-header" → Text("Accept-Language"))
      r.headers.statusCode shouldBe 200
    }

    "response filters" in {
      val request = DynamicRequest(HRL("/successfull"), Method.GET, DynamicBody(Text("test body")))
      val response = Created(DynamicBody("response body"))

      val filteredResponse = filterChain.filterResponse(RequestContext(request), response).runAsync.futureValue

      filteredResponse.body.content shouldBe Text("response body")
      filteredResponse.headers shouldNot contain("x-http-header" → Text("Accept-Language"))
      filteredResponse.headers.statusCode shouldBe 201
    }
  }
}

