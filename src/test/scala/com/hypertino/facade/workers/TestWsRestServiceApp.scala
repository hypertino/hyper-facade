package com.hypertino.facade.workers

import scaldi.Injector

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

class TestWsRestServiceApp(implicit inj: Injector) extends WsRestServiceApp {

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = {
    // todo: implement real stop
    super.stopService(controlBreak, timeout)
      .flatMap { _ ⇒
        hyperBus.shutdown(timeout * 3 / 5).timeout(timeout * 4 / 5).runAsync
      }
      .recover {
        case NonFatal(e) ⇒ log.error("Hyperbus didn't shutdown gracefully", e)
      }
      .flatMap { _ ⇒
        actorSystem.terminate()
      }
      .recover {
        case NonFatal(e) ⇒ log.error("ActorSystem wasn't terminated gracefully", e)
      }
      .map { _ ⇒
      }
  }
}
