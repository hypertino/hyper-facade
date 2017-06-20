package com.hypertino.facade.integration

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.workers.{HttpWorker, TestWsRestServiceApp, WsTestClientHelper}
import com.hypertino.facade.{FacadeConfigPaths, TestBase}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.api.Service
import monix.execution.{Cancelable, Scheduler}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration._

class IntegrationTestBase(val configFileName: String, val ramlFilePath: String) extends TestBase
  with BeforeAndAfterEach with WsTestClientHelper {

  System.setProperty(FacadeConfigPaths.RAML_FILE, ramlFilePath)

  implicit val injector = TestInjectors(configFileName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))
  implicit val timeout = akka.util.Timeout(30.seconds)
  implicit val scheduler = inject[Scheduler]

  val httpWorker = inject[HttpWorker]

  val app = inject[Service].asInstanceOf[TestWsRestServiceApp]
  app.start {
    httpWorker.restRoutes.routes
  }

//  inject[BasicAuthenticationService]

  val hyperbus = inject[Hyperbus] // initialize hyperbus
  //val testService = inject[TestService]
  val subscriptions = scala.collection.mutable.MutableList[Cancelable]()

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(1000)

  def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(_.cancel())
    subscriptions.clear
  }

  def register(s: Cancelable) = {
    subscriptions += s
  }
}

