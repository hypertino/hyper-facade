/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

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
