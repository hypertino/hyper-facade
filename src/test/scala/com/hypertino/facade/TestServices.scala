/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import akka.actor.ActorSystem
import com.hypertino.facade.modules.{FacadeServiceModule, FiltersModule, RamlConfigModule, SystemServicesModule}
import com.hypertino.facade.raml.{RamlConfiguration, RewriteIndexHolder}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigLoader
import com.typesafe.config.{Config, ConfigValueFactory}
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.asynchttpclient.DefaultAsyncHttpClient
import scaldi.{Injectable, Injector, Module}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestServices(configFileName: String, val ramlConfigFiles: Seq[String], extraModule: Injector, initHttpFacade: Boolean)
                  (protected implicit val scheduler: Scheduler, protected val timeout: akka.util.Timeout)
  extends AutoCloseable with Injectable {
  System.setProperty(FacadeConfigPaths.RAML_FILES, ramlConfigFiles.map(
    "src/test/resources/raml-configs/" + _
  ).mkString(java.io.File.pathSeparator))
  val httpPort = TestPortGenerator.next()
  val fullConfigPath = "./src/test/resources/" + configFileName
  implicit val injector = extraModule :: new FiltersModule :: new MetricsModule ::
    new SystemServicesModule :: new FacadeServiceModule :: new RamlConfigModule ::
    new TestConfigModule(httpPort, configFiles = Seq(fullConfigPath), failIfConfigNotFound = true, loadDefaults = true)

  injector.initNonLazy()

  implicit val actorSystem = inject[ActorSystem]

  val facadeService = if (initHttpFacade) inject[FacadeService] else null
  //  inject[BasicAuthenticationService]

  val hyperbus = inject[Hyperbus] // initialize hyperbus
  //val testService = inject[TestService]
  val subscriptions = scala.collection.mutable.MutableList[Cancelable]()
  val asyncHttpClient = new DefaultAsyncHttpClient
  val originalRamlConfig = inject[RamlConfiguration]('raml)

  hyperbus.startServices()
  if (facadeService != null) {
    facadeService.startService()
  }

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(500)

  override def close() {
    asyncHttpClient.close()
    subscriptions.foreach(_.cancel())
    subscriptions.clear

    RewriteIndexHolder.clearIndex()
    Await.result(Task.sequence(Seq(
      if (facadeService != null) Task.fromFuture(facadeService.stopService(false, 15.seconds)) else Task.unit,
      hyperbus.shutdown(5.seconds),
      Task.fromFuture(actorSystem.terminate())
    )).runAsync, 16.seconds)
  }
}

class TestConfigModule(port: Integer,
                       configFiles: Seq[String],
                       failIfConfigNotFound: Boolean,
                       loadDefaults: Boolean) extends Module {
  val rootConfig = ConfigLoader(configFiles, failIfConfigNotFound, loadDefaults).withValue("hyperfacade.http-transport.port", ConfigValueFactory.fromAnyRef(port))
  bind[Config] identifiedBy 'config toNonLazy rootConfig
}

