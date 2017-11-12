/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.model

import com.hypertino.hyperbus.model._

class FilterInterruptException(val response: DynamicResponse,
                               message: String,
                               cause: Throwable = null) extends Exception(message, cause)

class RewriteLimitReachedException(num: Int, max: Int) extends Exception(s"Maximum ($max) restart limits exceeded ($num)")

class RequestFormatException(s: String, cause: Throwable = null) extends Exception(s, cause)