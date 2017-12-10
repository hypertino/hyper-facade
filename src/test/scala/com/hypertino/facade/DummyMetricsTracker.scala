/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import com.codahale.metrics._
import com.hypertino.metrics.MetricsTracker

object DummyMetricsTracker extends MetricsTracker {
  override def gauge[T](name: String, gauge: Gauge[T]): Unit = {}

  override def meter(name: String): Meter = new Meter()

  override def counter(name: String): Counter = new Counter

  override def remove(name: String): Unit = {}

  override def histogram(name: String): Histogram = new Histogram(null)

  override def timer(name: String): Timer = new Timer()

  override def removeAll(): Unit = {}
}
