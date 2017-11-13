/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import com.hypertino.facade.workers.WsTestClientHelper
import monix.execution.Cancelable
import org.asynchttpclient.Response
import scala.collection.JavaConverters._
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

  def httpPostUrlEncoded(url: String, formData: Map[String, List[String]]): String = {
    val t = testObjects
    import t._
    val post = asyncHttpClient.preparePost(url)
    post.setFormParams(formData.map(kv ⇒ kv._1 → kv._2.asJava).asJava)
    val f = post.execute()

    Await.result(
      taskFromListenableFuture(f).runAsync.map { result ⇒
        result.getResponseBody
      },
      timeout.duration
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    Thread.sleep(500)
    testObjects = new TestServices(configFileName, ramlConfigFiles, extraModule, true)
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
