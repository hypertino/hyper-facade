package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.hyperbus.Hyperbus
import com.typesafe.config.Config
import monix.execution.Scheduler
import scaldi.Module

class SystemServicesModule extends Module {
  bind[Scheduler] identifiedBy 'scheduler to monix.execution.Scheduler.Implicits.global
  bind[Hyperbus] identifiedBy 'hyperbus to injected[Hyperbus]
  bind[ActorSystem] identifiedBy 'actorSystem to ActorSystem("facade", inject[Config])
}
