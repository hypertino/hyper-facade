package com.hypertino.facade.raml

import com.hypertino.hyperbus.model.HRL

object RewriteIndexHolder {

  var rewriteIndex = RewriteIndex()

  def updateRewriteIndex(sourceHRL: HRL, destHRL: HRL, method: Option[Method]): Unit = {
    val invertedIndex = rewriteIndex.inverted + (IndexKey(destHRL, method) → sourceHRL)
    val forwardIndex = rewriteIndex.forward + (IndexKey(sourceHRL, method) → destHRL)
    rewriteIndex = RewriteIndex(invertedIndex, forwardIndex)
  }

  def clearIndex(): Unit = {
    rewriteIndex = RewriteIndex()
  }
}
