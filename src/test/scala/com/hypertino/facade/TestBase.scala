package com.hypertino.facade

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import com.hypertino.facade.modules._
import com.hypertino.facade.raml.RewriteIndexHolder
import com.hypertino.facade.workers.WsTestClientHelper
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigModule
import com.hypertino.service.control.api.Service
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.asynchttpclient.{DefaultAsyncHttpClient, ListenableFuture}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest._
import scaldi.Injectable

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

abstract class TestBase(val configFileName: String = "inproc-test.conf", val ramlConfigFiles: Seq[String] = Seq(
  "simple.raml"
)) extends FlatSpec with Matchers with ScalaFutures
  with Injectable with BeforeAndAfterAll with BeforeAndAfterEach with WsTestClientHelper {

  System.setProperty(FacadeConfigPaths.RAML_FILES, ramlConfigFiles.map(
    "src/test/resources/raml-configs/" + _
  ).mkString(java.io.File.pathSeparator))
  val fullConfigPath = "./src/test/resources/" + configFileName
  implicit val injector = new FiltersModule :: new MetricsModule ::
    new SystemServicesModule :: new FacadeServiceModule :: new RamlConfigModule ::
    ConfigModule(configFiles=Seq(fullConfigPath), loadDefaults = true)

  injector.initNonLazy()

  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))
  implicit val timeout = akka.util.Timeout(30.seconds)
  implicit val scheduler = inject[Scheduler]

  val facadeService = inject[FacadeService]
  //  inject[BasicAuthenticationService]

  val hyperbus = inject[Hyperbus] // initialize hyperbus
  //val testService = inject[TestService]
  val subscriptions = scala.collection.mutable.MutableList[Cancelable]()

  // Unfortunately WsRestServiceApp doesn't provide a Future or any other way to ensure that listener is
  // bound to socket, so we need this stupid timeout to initialize the listener
  Thread.sleep(1000)

  val asyncHttpClient = new DefaultAsyncHttpClient

  def httpGet(url: String): String = {
    val f = asyncHttpClient.prepareGet(url).execute()
    Await.result(
      taskFromListenableFuture(f).runAsync.map { result ⇒
        result.getResponseBody
      },
      5.seconds
    )
  }

  def taskFromListenableFuture[T](lf: ListenableFuture[T]): Task[T] = Task.create { (scheduler, callback) ⇒
    lf.addListener(new Runnable {
      override def run(): Unit = {
        val r = Try(lf.get())
        callback(r)
      }
    }, null)
    new Cancelable {
      override def cancel(): Unit = lf.cancel(true)
    }
  }

  def newPoolExecutor(): Executor = {
    val maximumPoolSize: Int = Runtime.getRuntime.availableProcessors() * 16
    new ThreadPoolExecutor(0, maximumPoolSize, 5 * 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    Thread.sleep(400)
  }

  override def afterEach(): Unit = {
    subscriptions.foreach(_.cancel())
    subscriptions.clear
    super.afterEach()
  }

  def register(s: Cancelable) = {
    subscriptions += s
  }

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
    Await.result(Task.sequence(Seq(
      Task.fromFuture(facadeService.stopService(false, 15.seconds)),
      hyperbus.shutdown(5.seconds),
      Task.fromFuture(actorSystem.terminate())
    )).runAsync, 16.seconds)
    asyncHttpClient.close()
  }
}
