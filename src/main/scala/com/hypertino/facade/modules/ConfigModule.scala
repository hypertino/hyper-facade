package com.hypertino.facade.modules

import com.hypertino.facade.ConfigsFactory
import com.typesafe.config.Config
import com.hypertino.config.ConfigLoader
import com.hypertino.facade.raml.{RamlConfiguration, RamlConfigurationReader}
import scaldi.Module

class ConfigModule extends Module {
  bind [Config]                  identifiedBy 'config     toNonLazy ConfigLoader()
  bind [RamlConfiguration]       identifiedBy 'raml       to ConfigsFactory.ramlConfig( inject [Config] )
  bind [RamlConfigurationReader] identifiedBy 'ramlReader to injected[RamlConfigurationReader]
}
