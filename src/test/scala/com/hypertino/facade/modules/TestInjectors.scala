package com.hypertino.facade.modules

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigLoader
import scaldi.Injector

import scala.collection.JavaConversions._

object TestInjectors {
  def apply(configFileName: String): Injector = {
    val fullPath = "./src/test/resources/" + configFileName
    val injector = new TestConfigModule(fullPath) :: new FiltersModule :: loadConfigInjectedModules(fullPath, new TestServiceModule) :: new MetricsModule
    injector.initNonLazy()
  }

  def loadConfigInjectedModules(configFileName: String, previous: Injector): Injector = {
    val config = ConfigLoader(Seq(configFileName), failIfConfigNotFound=true, loadDefaults = false) // todo: replace with inject  if possible

    if (config.hasPath(FacadeConfigPaths.INJECT_MODULES)) {
      var module = previous
      config.getStringList(FacadeConfigPaths.INJECT_MODULES).foreach { injectModuleClassName ⇒
        module = module :: Class.forName(injectModuleClassName).newInstance().asInstanceOf[Injector]
      }
      module
    } else {
      previous
    }
  }
}
