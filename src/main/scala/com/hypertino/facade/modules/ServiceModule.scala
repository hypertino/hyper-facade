package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.facade.events.SubscriptionsManager
import com.typesafe.config.Config
import com.hypertino.facade.workers.{HttpWorker, WsRestServiceApp}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import com.hypertino.service.control.api.{Console, Service, ServiceController, ShutdownMonitor}
import monix.execution.Scheduler
import scaldi.Module

class ServiceModule extends Module {
  bind [Scheduler]              identifiedBy 'scheduler            to monix.execution.Scheduler.Implicits.global
  bind [Hyperbus]               identifiedBy 'hyperbus             to injected[Hyperbus]
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [HttpWorker]             identifiedBy 'httpWorker           to injected[HttpWorker]
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to injected[SubscriptionsManager]
  bind [Service]                identifiedBy 'restApp              to injected[WsRestServiceApp]
  bind [Console]                identifiedBy 'console              toNonLazy injected[StdConsole]
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected[ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected[RuntimeShutdownMonitor]
}
