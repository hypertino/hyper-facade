/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import java.util.concurrent.{Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import org.asynchttpclient.ListenableFuture
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import scaldi._

import scala.concurrent.duration._
import scala.util.Try

abstract class TestBase extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll with BeforeAndAfterEach {

  implicit val scheduler: Scheduler = Scheduler.Implicits.global
  implicit val timeout = akka.util.Timeout(30.seconds)
  implicit val patience = PatienceConfig(scaled(Span(60, Seconds)))

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

  def extraModule: Injector = NilInjector
}
