package com.hypertino.facade

import com.hypertino.facade.workers.WsTestClientHelper
import monix.execution.{Cancelable, Scheduler}
import org.asynchttpclient.Response

import scala.concurrent.Await

abstract class TestBaseWithFacade(
                                   val configFileName: String = "inproc-test.conf",
                                   val ramlConfigFiles: Seq[String] = Seq("simple.raml")
                                 ) extends TestBase with WsTestClientHelper {
  protected var testObjects: TestServices = null

  def httpGet(url: String): String = {
    val t = testObjects
    import t._
    val f = asyncHttpClient.prepareGet(url).execute()
    Await.result(
      taskFromListenableFuture(f).runAsync.map { result ⇒
        result.getResponseBody
      },
      timeout.duration
    )
  }

  def httpGetResponse(url: String): Response = {
    val t = testObjects
    import t._
    val f = asyncHttpClient.prepareGet(url).execute()
    Await.result(
      taskFromListenableFuture(f).runAsync.map { result ⇒
        result
      },
      timeout.duration
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    Thread.sleep(500)
    testObjects = new TestServices(configFileName,ramlConfigFiles,extraModule,true)
  }

  override def afterEach(): Unit = {
    if (testObjects != null) {
      testObjects.close()
      testObjects = null
    }
  }

  def register(s: Cancelable) = {
    testObjects.subscriptions += s
    Thread.sleep(500)
  }

  def register(s: Seq[Cancelable]) = {
    testObjects.subscriptions ++= s
    Thread.sleep(500)
  }
}
