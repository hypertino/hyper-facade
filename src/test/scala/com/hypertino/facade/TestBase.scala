package com.hypertino.facade

import com.hypertino.facade.raml.RewriteIndexHolder
import com.hypertino.facade.workers.TestWsRestServiceApp
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}
import scaldi.Injectable

abstract class TestBase extends FreeSpec with Matchers with ScalaFutures with Injectable with BeforeAndAfterAll {

  def app: TestWsRestServiceApp
  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
    app.stopService(true)
  }
}
