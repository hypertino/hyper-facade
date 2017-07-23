package com.hypertino.facade.filter.chain

import com.hypertino.binders.value.{Null, Text}
import com.hypertino.facade.filter.model.{RequestFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.{DefaultPredicateEvaluator, PredicateEvaluator}
import com.hypertino.facade.model._
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, DynamicResponse, EmptyBody, ErrorBody, Forbidden, HRL, Headers, Method, Ok}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import com.hypertino.hyperbus.model._

class FilterChainTest extends FreeSpec with Matchers with ScalaFutures {

  val filterChain = SimpleFilterChain(
    requestFilters = Seq(new TestRequestFilter),
    responseFilters = Seq(new TestResponseFilter)
  ) // todo: + test eventFilters

  class TestRequestFilter extends RequestFilter {
    override protected def predicateEvaluator: PredicateEvaluator = DefaultPredicateEvaluator
    override def  apply(contextWithRequest: ContextWithRequest)
             (implicit ec: ExecutionContext): Future[ContextWithRequest] = {
      if (contextWithRequest.request.headers.hrl.location != "/interrupted") {
        Future(contextWithRequest)
      }
      else {
        implicit val mcx = contextWithRequest.request
        Future.failed(new FilterInterruptException(
          response = Forbidden(ErrorBody("Forbidden")),
          message = "Forbidden by filter"
        ))
      }
    }
  }

  class TestResponseFilter extends ResponseFilter {
    override protected def predicateEvaluator: PredicateEvaluator = DefaultPredicateEvaluator
    override def apply(contextWithRequest: ContextWithRequest, output: DynamicResponse)
                      (implicit ec: ExecutionContext): Future[DynamicResponse] = {
      if (contextWithRequest.request.headers.hrl.location != "/interrupted") {
        Future(output)
      }
      else {
        implicit val mcx = contextWithRequest.request
        Future.failed(new FilterInterruptException(
          response = Ok(EmptyBody, Headers.builder.+=("x-http-header" → "Accept-Language").result()),
          message = "Interrupted by filter"
        ))
      }
    }
  }

  import MessagingContext.Implicits.emptyContext

  "FilterChain " - {
    "request filters with interruption" in {
      val request = DynamicRequest(HRL("/interrupted"), Method.GET, DynamicBody(Text("test body")))

      val interrupt = intercept[FilterInterruptException] {
        filterChain.filterRequest(ContextWithRequest(request)).awaitFuture
      }

      interrupt.response shouldBe a[Forbidden[_]]
    }

    "request filters" in {
      val request = DynamicRequest(HRL("/successfull"), Method.GET, DynamicBody(Text("test body")))

      val filteredRequest = filterChain.filterRequest(ContextWithRequest(request)).futureValue.request

      filteredRequest.body shouldBe DynamicBody(Text("test body"))
      filteredRequest.headers.hrl shouldBe HRL("/successfull")
      filteredRequest.headers.method shouldBe Method.GET
    }

    "response filters with interruption" in {
      val request = DynamicRequest(HRL("/interrupted"), Method.GET, DynamicBody(Text("test body")))
      val response = Created(DynamicBody("response body"))

      val interrupt = intercept[FilterInterruptException] {
        filterChain.filterResponse(ContextWithRequest(request), response).awaitFuture
      }

      interrupt.response.body.content shouldBe Null
      interrupt.response.headers should contain("x-http-header" → Text("Accept-Language"))
      interrupt.response.headers.statusCode shouldBe 200
    }

    "response filters" in {
      val request = DynamicRequest(HRL("/successfull"), Method.GET, DynamicBody(Text("test body")))
      val response = Created(DynamicBody("response body"))

      val filteredResponse = filterChain.filterResponse(ContextWithRequest(request), response).futureValue

      filteredResponse.body.content shouldBe Text("response body")
      filteredResponse.headers shouldNot contain("x-http-header" → Text("Accept-Language"))
      filteredResponse.headers.statusCode shouldBe 201
    }
  }


  implicit class TestAwait[T](future: Future[T]) {
    def awaitFuture: T = {
      Await.result(future, 10.seconds)
    }
  }
}

