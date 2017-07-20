package com.hypertino.facade

import com.hypertino.facade.modules._
import com.hypertino.service.config.ConfigModule
import com.hypertino.service.control.api.ServiceController
import scaldi.Injectable

object MainApp extends App with Injectable {
  implicit val injector = new SystemServiceModule ::
    new FacadeServiceModule ::
    new FiltersModule ::
    new RamlConfigModule ::
    ConfigModule()

  inject[ServiceController].run()
}
