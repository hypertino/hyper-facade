/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.metrics

object MetricKeys {
  final val ACTIVE_CONNECTIONS = "hyperfacade.http.active-connections"
  final val REJECTED_CONNECTS = "hyperfacade.http.rejected-connects"
  final val WS_LIFE_TIME = "hyperfacade.http.ws-life-time"
  final val WS_MESSAGE_COUNT = "hyperfacade.http.ws-message-count"
  final val HEARTBEAT = "hyperfacade.heartbeat"
  final val TOTAL_REQUEST_TIME = "hyperfacade.total-request-time"
  final val SPECIFIC_REQUEST_TIME_PREFIX = "hyperfacade.requests"
  final val SPECIFIC_FILTER_TIME_PREFIX = "hyperfacade.filters"
  final val SPECIFIC_FIELD_FILTER_TIME_PREFIX = "hyperfacade.fields"

  private final val MAX_SPECIFIC_RESOURCES = 10000
  private final val MAX_SPECIFIC_FILTERS = 10000
  private final val MAX_SPECIFIC_FIELD_FILTERS = 10000

  private val specificResourceMap = new MetricKeyNameMap(MAX_SPECIFIC_RESOURCES, SPECIFIC_REQUEST_TIME_PREFIX + ".EXCEEDED",
    s ⇒ SPECIFIC_REQUEST_TIME_PREFIX + "." + sanitizeKeySegment(s)
  )
  private val specificFilterMap = new MetricKeyNameMap(MAX_SPECIFIC_FILTERS, SPECIFIC_FILTER_TIME_PREFIX + ".EXCEEDED",
    s ⇒ SPECIFIC_FILTER_TIME_PREFIX + "." + sanitizeKeySegment(s)
  )
  private val specificFieldFilterMap = new MetricKeyNameMap(MAX_SPECIFIC_FIELD_FILTERS, SPECIFIC_FIELD_FILTER_TIME_PREFIX + ".EXCEEDED",
    s ⇒ SPECIFIC_FIELD_FILTER_TIME_PREFIX + "." + sanitizeKeySegment(s)
  )

  def specificRequest(hrl: String): String = specificResourceMap.keyNameFor(hrl)
  def specificFilter(filterName: String): String = specificFilterMap.keyNameFor(filterName)
  def specificFieldFilter(fieldName: String): String = specificFieldFilterMap.keyNameFor(fieldName)

  def sanitizeKeySegment(s: String, maxLength: Int = 50): String = {
    var nextUpper: Boolean = true
    val sb = new StringBuilder

    for (i ← 0 until Math.min(maxLength, s.length)) {
      val c = s.charAt(i)
      if ((c >= 'a' && c <= 'z') ||
        (c >= 'A' && c <= 'Z') ||
        (c >= '0' && c <= '9') ||
        (c == '-' || c == '~')) {
        if (nextUpper)
          sb.append(c.toUpper)
        else
          sb.append(c)
        nextUpper = false
      }
      else {
        nextUpper = true
      }
    }
    sb.toString
  }
}

