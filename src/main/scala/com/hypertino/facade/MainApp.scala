package com.hypertino.facade

import com.hypertino.facade.modules._
import com.hypertino.service.config.ConfigModule
import scaldi.Injectable

// todo: reconsider MainApp, why we need this?
object MainApp extends App with Injectable {
  implicit val injector = new SystemServiceModule ::
    new FacadeServiceModule ::
    new FiltersModule ::
    new RamlConfigModule ::
    ConfigModule()

  inject[FacadeService]
}
