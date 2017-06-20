package com.hypertino.facade

import com.hypertino.facade.raml.RewriteIndexHolder
import org.scalatest.{BeforeAndAfterAll, Suite}

/**
  * Created by maqdev on 6/12/17.
  */
trait CleanRewriteIndex extends BeforeAndAfterAll {
  this: Suite ⇒

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
  }
}
