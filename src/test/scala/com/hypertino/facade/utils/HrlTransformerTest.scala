package com.hypertino.facade.utils

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service

class HrlTransformerTest extends TestBase {

  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/uri-transformer-test.raml")
  implicit val injector = TestInjectors()
  val ramlConfig = inject[RamlConfiguration]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  "UriTransformerTest" - {
    "Rewrite backward" in {
      val r = HrlTransformer.rewriteLinkToOriginal(Uri("/rewritten-events/root/1"), 1)
      r shouldBe Uri("/events/1")
    }

    "Rewrite backward (templated)" in {
      val r = HrlTransformer.rewriteLinkToOriginal(Uri("/rewritten-events/{path:*}", Map("path" → "root/1")), 1)
      r shouldBe Uri("/events/1")
    }

    "Rewrite forward" in {
      val r = HrlTransformer.rewriteLinkForward(Uri("/events/25"), 1, ramlConfig)
      r shouldBe Uri("/rewritten-events/root/{path:*}", Map("path" → "25"))
    }

    "Rewrite forward (templated)" in {
      val r = HrlTransformer.rewriteLinkForward(Uri("/events/{path}",Map("path" → "25")), 1, ramlConfig)
      r shouldBe Uri("/rewritten-events/root/{path:*}", Map("path" → "25"))
    }

    "Do not rewrite forward legacy resource" in {
      val r = HrlTransformer.rewriteLinkForward(Uri("/events/legacy"), 1, ramlConfig)
      r shouldBe Uri("/events/legacy")
    }
  }
}