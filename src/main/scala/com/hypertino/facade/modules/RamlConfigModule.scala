package com.hypertino.facade.modules

import com.hypertino.facade.ConfigsFactory
import com.typesafe.config.Config
import com.hypertino.facade.raml.{RamlConfiguration}
import scaldi.Module

class RamlConfigModule extends Module {
  bind [RamlConfiguration]       identifiedBy 'raml       to ConfigsFactory.ramlConfig( inject [Config] )
}
