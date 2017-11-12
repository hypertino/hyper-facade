/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.modules

import akka.actor.ActorSystem
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.transport.api.ServiceRegistrator
import com.hypertino.hyperbus.transport.registrators.DummyRegistrator
import com.typesafe.config.Config
import monix.execution.Scheduler
import scaldi.Module

class SystemServicesModule extends Module {
  bind[Scheduler] to monix.execution.Scheduler.Implicits.global
  bind[Hyperbus] to injected[Hyperbus]
  bind[ActorSystem] to ActorSystem("facade", inject[Config])
  bind[ServiceRegistrator] to DummyRegistrator
}
