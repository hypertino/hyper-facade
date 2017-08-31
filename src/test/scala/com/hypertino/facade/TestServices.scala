package com.hypertino.facade

import akka.actor.ActorSystem
import com.hypertino.facade.modules.{FacadeServiceModule, FiltersModule, RamlConfigModule, SystemServicesModule}
import com.hypertino.facade.raml.{RamlConfiguration, RewriteIndexHolder}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigModule
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.asynchttpclient.DefaultAsyncHttpClient
import scaldi.{Injectable, Injector}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestServices(configFileName: String, val ramlConfigFiles: Seq[String], extraModule: Injector)
                  (protected implicit val scheduler: Scheduler, protected val timeout: akka.util.Timeout)
  extends AutoCloseable with Injectable {
  System.setProperty(FacadeConfigPaths.RAML_FILES, ramlConfigFiles.map(
    "src/test/resources/raml-configs/" + _
  ).mkString(java.io.File.pathSeparator))
  val fullConfigPath = "./src/test/resources/" + configFileName
  implicit val injector = extraModule :: new FiltersModule :: new MetricsModule ::
    new SystemServicesModule :: new FacadeServiceModule :: new RamlConfigModule ::
    ConfigModule(configFiles=Seq(fullConfigPath), loadDefaults = true)

  injector.initNonLazy()

  implicit val actorSystem = inject[ActorSystem]

  val facadeService = inject[FacadeService]
  //  inject[BasicAuthenticationService]

  val hyperbus = inject[Hyperbus] // initialize hyperbus
  //val testService = inject[TestService]
  val subscriptions = scala.collection.mutable.MutableList[Cancelable]()
  val asyncHttpClient = new DefaultAsyncHttpClient
  val originalRamlConfig = inject[RamlConfiguration]('raml)

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(500)

  override def close() {
    subscriptions.foreach(_.cancel())
    subscriptions.clear

    RewriteIndexHolder.clearIndex()
    Await.result(Task.sequence(Seq(
      Task.fromFuture(facadeService.stopService(false, 15.seconds)),
      hyperbus.shutdown(5.seconds),
      Task.fromFuture(actorSystem.terminate())
    )).runAsync, 16.seconds)
    asyncHttpClient.close()
  }
}
