package com.hypertino.facade.utils

import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.HRL
import org.scalatest.{FreeSpec, Matchers}

class ResourcePatternMatcherSpec extends FreeSpec with Matchers {

  "URI matcher" - {
    "match URI with pattern from RAML" in {
      val parameterRegularMatch = ResourcePatternMatcher.matchResource("http://a/unreliable-feed/someContent", "http://a/unreliable-feed/{content}")
      parameterRegularMatch shouldBe Some(HRL("http://a/unreliable-feed/{content}", Obj.from("content" → "someContent")))

      val parameterLongPathMatch = ResourcePatternMatcher.matchResource("http://a/reliable-feed/someContent/someDetails", "http://a/reliable-feed/{content:*}")
      parameterLongPathMatch shouldBe Some(HRL("http://a/reliable-feed/{content:*}", Obj.from("content" → "someContent/someDetails")))

      val parameterShortPathMatch = ResourcePatternMatcher.matchResource("http://a/revault/content/abc", "http://a/revault/content/{path:*}")
      parameterShortPathMatch shouldBe Some(HRL("http://a/revault/content/{path:*}", Obj.from("path" → "abc")))

//      val pathToPathMatch = ResourcePatternMatcher.matchResource("/rewritten-events/root/{path:*}", Uri("/rewritten-events/{path:*}", Map("path" → "root/1")))
//      pathToPathMatch shouldBe Some(HRL("/rewritten-events/root/{path:*}", Obj.from("path" → "1")))

      val doubleSlashRequest = ResourcePatternMatcher.matchResource("http://a/revault//content/abc", "http://a/revault/content/{path:*}")
      doubleSlashRequest shouldBe Some(HRL("http://a/revault/content/{path:*}", Obj.from("path" → "abc")))

      val tripleSlashRequest = ResourcePatternMatcher.matchResource("http://a/revault///content/abc", "http://a/revault/content/{path:*}")
      tripleSlashRequest shouldBe Some(HRL("http://a/revault/content/{path:*}", Obj.from("path" → "abc")))
    }
  }
}

