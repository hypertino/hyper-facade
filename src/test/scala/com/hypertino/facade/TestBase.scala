package com.hypertino.facade

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import akka.actor.ActorSystem
import com.hypertino.facade.modules.TestInjectors
import com.hypertino.facade.raml.RewriteIndexHolder
import com.hypertino.facade.workers.WsTestClientHelper
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.service.control.api.Service
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}
import scaldi.Injectable

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class TestBase(val configFileName: String) extends FreeSpec with Matchers with ScalaFutures
  with Injectable with BeforeAndAfterAll with BeforeAndAfterEach with WsTestClientHelper {

  implicit val injector = TestInjectors(configFileName)
  implicit val actorSystem = inject[ActorSystem]
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))
  implicit val timeout = akka.util.Timeout(30.seconds)
  implicit val scheduler = inject[Scheduler]
  val service = new FacadeService()
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

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
    Await.result(Task.sequence(Seq(
      Task.fromFuture(service.stopService(false, 15.seconds)),
      hyperbus.shutdown(5.seconds),
      Task.fromFuture(actorSystem.terminate())
    )).runAsync, 16.seconds)
  }
}
