/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
