package com.hypertino.facade.modules

import com.hypertino.facade.ConfigsFactory
import com.hypertino.facade.raml.{RamlConfiguration, RamlConfigurationReader}
import com.hypertino.service.config.ConfigLoader
import com.typesafe.config.Config
import scaldi.Module

class TestConfigModule(configFileName: String) extends Module {
  bind [Config]                  identifiedBy 'config     toNonLazy ConfigLoader(Seq(configFileName), failIfConfigNotFound = true, loadDefaults = true)
  bind [RamlConfiguration]       identifiedBy 'raml       to ConfigsFactory.ramlConfig( inject [Config] )
  bind [RamlConfigurationReader] identifiedBy 'ramlReader to injected[RamlConfigurationReader]
}
