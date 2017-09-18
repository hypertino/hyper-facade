package com.hypertino.facade

import monix.execution.atomic.AtomicInt

object TestPortGenerator {
  private val port = AtomicInt(56001)
  def next(): Int = port.incrementAndGet()
}
