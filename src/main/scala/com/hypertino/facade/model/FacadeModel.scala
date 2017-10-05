package com.hypertino.facade.model

import com.hypertino.hyperbus.model._

class FilterInterruptException(val response: DynamicResponse,
                               message: String,
                               cause: Throwable = null) extends Exception(message, cause)

class RewriteLimitReachedException(num: Int, max: Int) extends Exception(s"Maximum ($max) restart limits exceeded ($num)")

class RequestFormatException(s: String, cause: Throwable = null) extends Exception(s, cause)