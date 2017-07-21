package com.hypertino.facade

import com.hypertino.facade.modules._
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigModule
import com.hypertino.service.control.api.{Service, ServiceController}
import scaldi.{Injectable, Module}

class MainServiceModule extends Module {
  bind [Service]          identifiedBy 'mainService        to inject[FacadeService]
}

object MainApp extends App with Injectable {
  implicit val injector = new SystemServiceModule ::
    new MetricsModule
    new FacadeServiceModule ::
    new MainServiceModule ::
    new FiltersModule ::
    new RamlConfigModule ::
    ConfigModule()

  inject[ServiceController].run()
}
