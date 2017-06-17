package com.hypertino.facade.raml

import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.transport.api.uri.Uri
import org.scalatest.{FreeSpec, Matchers}

class ResourcePatternMatcherTest extends FreeSpec with Matchers {

  "URI matcher" - {
    "match URI with pattern from RAML" in {
      val parameterRegularMatch = ResourcePatternMatcher.matchResource("/unreliable-feed/{content}", Uri("/unreliable-feed/someContent"))
      parameterRegularMatch shouldBe Some(Uri("/unreliable-feed/{content}", Map("content" → "someContent")))

      val parameterLongPathMatch = ResourcePatternMatcher.matchResource("/reliable-feed/{content:*}", Uri("/reliable-feed/someContent/someDetails"))
      parameterLongPathMatch shouldBe Some(Uri("/reliable-feed/{content:*}", Map("content" → "someContent/someDetails")))

      val parameterShortPathMatch = ResourcePatternMatcher.matchResource("/revault/content/{path:*}", Uri("/revault/content/abc"))
      parameterShortPathMatch shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))

      val pathToPathMatch = ResourcePatternMatcher.matchResource("/rewritten-events/root/{path:*}", Uri("/rewritten-events/{path:*}", Map("path" → "root/1")))
      pathToPathMatch shouldBe Some(Uri("/rewritten-events/root/{path:*}", Map("path" → "1")))

      val doubleSlashRequest = ResourcePatternMatcher.matchResource("/revault/content/{path:*}", Uri("/revault//content/abc"))
      doubleSlashRequest shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))

      val tripleSlashRequest = ResourcePatternMatcher.matchResource("/revault/content/{path:*}", Uri("/revault///content/abc"))
      tripleSlashRequest shouldBe Some(Uri("/revault/content/{path:*}", Map("path" → "abc")))
    }
  }
}
