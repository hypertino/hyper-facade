package com.hypertino.facade.modules

import com.hypertino.facade.ConfigsFactory
import com.hypertino.facade.raml.RamlConfiguration
import com.typesafe.config.Config
import scaldi.Module

class RamlConfigModule extends Module {
  bind[RamlConfiguration] identifiedBy 'raml to ConfigsFactory.ramlConfig(inject[Config])
}
