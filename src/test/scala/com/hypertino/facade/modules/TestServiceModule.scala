package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.facade.events.SubscriptionsManager
import com.typesafe.config.Config
import com.hypertino.facade.workers.{HttpWorker, TestWsRestServiceApp}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.api.Service
import monix.execution.Scheduler
import scaldi.Module

import scala.concurrent.ExecutionContext

/**
  * This class is needed to inject TestWsRestServiceApp - modified version of WsRestServiceApp without a shutdown hook.
  * Since test suites run one-by-one, we don't need to multiple shutdown hooks triggers on the end of a test run
  */
class TestServiceModule extends Module {
  bind [TestHyperbusFactory]    identifiedBy 'hbFactory            toProvider new TestHyperbusFactory(inject [Config], injector)
  bind [Hyperbus]               identifiedBy 'hyperbus             to inject [TestHyperbusFactory].hyperbus
  bind [ActorSystem]            identifiedBy 'actorSystem          to ActorSystem("facade", inject [Config])
  bind [Scheduler]              identifiedBy 'scheduler            to monix.execution.Scheduler.Implicits.global
  //bind [ExecutionContext]       identifiedBy 'executionContext     to inject[ActorSystem].dispatcher
  bind [HttpWorker]             identifiedBy 'httpWorker           to injected[HttpWorker]
  bind [SubscriptionsManager]   identifiedBy 'subscriptionsManager to injected[SubscriptionsManager]
  bind [Service]                identifiedBy 'restApp              to injected[TestWsRestServiceApp]
}
