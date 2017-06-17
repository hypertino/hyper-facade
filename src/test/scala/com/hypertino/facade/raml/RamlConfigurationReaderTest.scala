package com.hypertino.facade.raml

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.workers.TestWsRestServiceApp
import com.hypertino.facade.TestBase
import com.hypertino.hyperbus.transport.api.uri.Uri
import com.hypertino.servicecontrol.api.Service

class RamlConfigurationReaderTest extends TestBase {
  System.setProperty(FacadeConfigPaths.RAML_FILE, "raml-configs/raml-reader-test.raml")
  System.setProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, "true")
  implicit val injector = TestInjectors()
  val ramlReader = inject[RamlConfigurationReader]
  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]

  override def afterAll(): Unit = {
    System.setProperty(FacadeConfigPaths.RAML_STRICT_CONFIG, "false")
  }

  "RamlConfigurationReader" - {
    "missing resource" in {
      intercept[RamlStrictConfigException] {
        ramlReader.resourceHRL(Uri("/missing-resource"), "get")
      }
    }

    "existing resource, not configured method" in {
      ramlReader.resourceHRL(Uri("/resource"), "get")
      intercept[RamlStrictConfigException] {
        ramlReader.resourceHRL(Uri("/resource"), "post")
      }
    }
  }
}
