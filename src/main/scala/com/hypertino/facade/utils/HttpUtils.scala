/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.hypertino.binders.value.{Bool, Null, Number, Obj, Text, Value}
import com.typesafe.scalalogging.StrictLogging
import spray.http.HttpHeaders.{Cookie, RawHeader}
import spray.http.parser.HttpParser

object HttpUtils extends StrictLogging {
  def parseCookies(v: Value): Value = {
    v match {
      case Null ⇒ Null
      case other ⇒
        HttpParser.parseHeader(RawHeader("cookie", other.toString)) match {
          case Right(Cookie(cookies)) ⇒
            Obj.from(cookies.map { c ⇒
              val m = Seq(
                "name" → Text(c.name),
                "content" → Text(c.content),
                "http_only" → Bool(c.httpOnly),
                "secure"→ Bool(c.secure)
              ) ++
                c.domain.map(d ⇒ "domain" → Text(d)) ++
                c.extension.map(d ⇒ "extension" → Text(d)) ++
                c.path.map(d ⇒ "path" → Text(d)) ++
                c.maxAge.map(d ⇒ "max_age" → Number(d)) ++
                c.expires.map(e ⇒ "expires" → Text(e.toRfc1123DateTimeString))

              c.name → Obj.from(m:_*)
            }:_*)

          case Left(er) ⇒
            logger.error(s"Can't parse cookie header: $er")
            Null
        }
    }
  }
}
