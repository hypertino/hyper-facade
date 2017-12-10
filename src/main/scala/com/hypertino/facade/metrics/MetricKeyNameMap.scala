/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.metrics

import monix.execution.atomic.AtomicInt

import scala.collection.concurrent.TrieMap

class MetricKeyNameMap(maxKeys: Int, exceededName: String, formatter: (String) ⇒ String) {
  private val nameMap = TrieMap[String, String]()
  private val count = AtomicInt(0)
  def keyNameFor(raw: String): String = {
    if (count.get > maxKeys) {
      exceededName
    }
    else {
      nameMap.getOrElseUpdate(raw, {
        count.increment()
        formatter(raw)
      })
    }
  }
}
