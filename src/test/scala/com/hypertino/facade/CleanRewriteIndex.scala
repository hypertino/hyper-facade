package com.hypertino.facade

/**
  * Created by maqdev on 6/12/17.
  */
trait CleanRewriteIndex extends BeforeAndAfterAll {
  this: Suite ⇒

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
  }
}
