package com.hypertino.facade

import akka.actor.ActorSystem
import com.hypertino.facade.modules._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.metrics.modules.MetricsModule
import com.hypertino.service.config.ConfigModule
import com.hypertino.service.control.ConsoleModule
import com.hypertino.service.control.api.{Service, ServiceController}
import monix.execution.Scheduler
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Module}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

class MainServiceModule extends Module {
  bind [Service]          identifiedBy 'mainService        to inject[FacadeService]
}

object MainApp extends App with Injectable {
  private implicit val injector =
    new ConsoleModule ::
    new SystemServicesModule ::
    new MetricsModule ::
    new FacadeServiceModule ::
    new MainServiceModule ::
    new FiltersModule ::
    new RamlConfigModule ::
    ConfigModule()

  private implicit val scheduler = inject[Scheduler]
  private val log = LoggerFactory.getLogger(getClass)

  inject[ServiceController].run().andThen {
    case _ ⇒
      val timeout = 10.seconds
      Try{Await.result(inject[Hyperbus].shutdown(timeout).runAsync, timeout + 0.5.seconds)}.recover(logException)
      Try{Await.result(inject[ActorSystem].terminate(), timeout + 0.5.seconds)}.recover(logException)
  }

  private def logException: PartialFunction[Throwable, Unit] = {
    case NonFatal(e) ⇒ log.error("Unhandled exception", e)
  }
}
