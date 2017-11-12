/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.HRL
import org.scalatest.{FreeSpec, Matchers}

class ResourcePatternMatcherSpec extends FreeSpec with Matchers {

  "URI matcher" - {
    "match URI with pattern from RAML" in {
      val parameterRegularMatch = ResourcePatternMatcher.matchResource(HRL("http://a/unreliable-feed/someContent"), HRL("http://a/unreliable-feed/{content}"))
      parameterRegularMatch shouldBe Some(HRL("http://a/unreliable-feed/{content}", Obj.from("content" → "someContent")))

//      val parameterLongPathMatch = ResourcePatternMatcher.matchResource(HRL("http://a/reliable-feed/someContent/someDetails"), HRL("http://a/reliable-feed/{content:*}"))
//      parameterLongPathMatch shouldBe Some(HRL("http://a/reliable-feed/{content:*}", Obj.from("content" → "someContent/someDetails")))
//
//      val parameterShortPathMatch = ResourcePatternMatcher.matchResource(HRL("http://a/revault/content/abc"), HRL("http://a/revault/content/{path}"))
//      parameterShortPathMatch shouldBe Some(HRL("http://a/revault/content/{path:*}", Obj.from("path" → "abc")))
//      val pathToPathMatch = ResourcePatternMatcher.matchResource("/rewritten-events/root/{path:*}", Uri("/rewritten-events/{path:*}", Map("path" → "root/1")))
//      pathToPathMatch shouldBe Some(HRL("/rewritten-events/root/{path:*}", Obj.from("path" → "1")))

      val doubleSlashRequest = ResourcePatternMatcher.matchResource(HRL("http://a/revault//content/abc"), HRL("http://a/revault/content/{path}"))
      doubleSlashRequest shouldBe Some(HRL("http://a/revault/content/{path}", Obj.from("path" → "abc")))

      val tripleSlashRequest = ResourcePatternMatcher.matchResource(HRL("http://a/revault///content/abc"), HRL("http://a/revault/content/{path}"))
      tripleSlashRequest shouldBe Some(HRL("http://a/revault/content/{path}", Obj.from("path" → "abc")))
    }
  }
}

