package com.hypertino.facade.modules

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import com.typesafe.config.Config
import scaldi.Injector

class TestHyperbusFactory(val config: Config, inj: Injector) {
  lazy val hyperbus = new Hyperbus(newTransportManager(), TestHyperbusFactory.defaultHyperbusGroup(config), true)

  private def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  private def newTransportManager(): TransportManager = {
    new TransportManager(TransportConfigurationLoader.fromConfig(config, inj))(inj)
  }
}

object TestHyperbusFactory {
  def defaultHyperbusGroup(config: Config) = {
    if (config.hasPath(FacadeConfigPaths.HYPERBUS_GROUP))
      Some(config.getString(FacadeConfigPaths.HYPERBUS_GROUP))
    else None
  }
}
