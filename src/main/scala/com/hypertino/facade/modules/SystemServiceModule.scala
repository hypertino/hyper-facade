package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.facade.FacadeService
import com.hypertino.facade.events.SubscriptionsManager
import com.hypertino.facade.workers.HttpWorker
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.api.{Console, ServiceController, ShutdownMonitor}
import com.hypertino.service.control.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import com.typesafe.config.Config
import monix.execution.Scheduler
import scaldi.Module

class SystemServiceModule extends Module {
  bind [Scheduler]              identifiedBy 'scheduler            to monix.execution.Scheduler.Implicits.global
  bind [Hyperbus]               identifiedBy 'hyperbus             to injected[Hyperbus]
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [Console]                identifiedBy 'console              toNonLazy injected[StdConsole]
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected[ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected[RuntimeShutdownMonitor]
}
