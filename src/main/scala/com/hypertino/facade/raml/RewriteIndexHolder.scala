package com.hypertino.facade.raml

import com.hypertino.hyperbus.model.HRL

object RewriteIndexHolder {

  var rewriteIndex = RewriteIndex()

  def updateRewriteIndex(originalUri: HRL, rewrittenUri: HRL, method: Option[Method]): Unit = {
    val invertedIndex = rewriteIndex.inverted + (IndexKey(rewrittenUri, method) → originalUri)
    val forwardIndex = rewriteIndex.forward + (IndexKey(originalUri, method) → rewrittenUri)
    rewriteIndex = RewriteIndex(invertedIndex, forwardIndex)
  }

  def clearIndex(): Unit = {
    rewriteIndex = RewriteIndex()
  }
}
