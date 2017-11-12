/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.metrics

object MetricKeys {
  final val ACTIVE_CONNECTIONS = "http.active-connections"
  final val REJECTED_CONNECTS = "http.rejected-connects"
  final val WS_LIFE_TIME = "http.ws-life-time"
  final val WS_MESSAGE_COUNT = "http.ws-message-count"
  final val REQUEST_PROCESS_TIME = "request.process-time"
  final val HEARTBEAT = "heartbeat"
}
