package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.facade.HyperbusFactory
import com.hypertino.facade.events.SubscriptionsManager
import com.typesafe.config.Config
import com.hypertino.facade.workers.{HttpWorker, WsRestServiceApp}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.{ConsoleServiceController, RuntimeShutdownMonitor, StdConsole}
import com.hypertino.service.control.api.{Console, Service, ServiceController, ShutdownMonitor}
import scaldi.Module

import scala.concurrent.ExecutionContext

class ServiceModule extends Module {
  bind [HyperbusFactory]        identifiedBy 'hbFactory            to new HyperbusFactory(inject [Config], injector)
  bind [Hyperbus]               identifiedBy 'hyperbus             to inject [HyperbusFactory].hyperbus
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [ExecutionContext]       identifiedBy 'executionContext     to inject[ActorSystem].dispatcher
  bind [HttpWorker]             identifiedBy 'httpWorker           to injected[HttpWorker]
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to injected[SubscriptionsManager]
  bind [Service]                identifiedBy 'restApp              to injected[WsRestServiceApp]
  bind [Console]                identifiedBy 'console              toNonLazy injected[StdConsole]
  bind [ServiceController]      identifiedBy 'serviceController    toNonLazy injected[ConsoleServiceController]
  bind [ShutdownMonitor]        identifiedBy 'shutdownMonitor      toNonLazy injected[RuntimeShutdownMonitor]
}
