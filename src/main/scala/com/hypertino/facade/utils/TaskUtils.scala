package com.hypertino.facade.utils

import monix.eval.Task

object TaskUtils {
  def chain[A](initial: A, sequence: Seq[A => Task[A]]): Task[A] = {
    sequence.headOption match {
      case None =>
        Task.now(initial)

      case Some(task) =>
        task(initial).flatMap { result =>
          chain(result, sequence.tail)
        }
    }
  }
}
