/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.hypertino.binders.value.Obj
import com.hypertino.facade.metrics.MetricKeys
import org.scalatest.{FlatSpec, Matchers}

class MetricKeysSpec extends FlatSpec with Matchers {
  "MetricKeys" should "sanitize metric key" in {
    val s = MetricKeys.sanitizeKeySegment("/abcde/{path}")
    s shouldBe "AbcdePath"
  }
}

