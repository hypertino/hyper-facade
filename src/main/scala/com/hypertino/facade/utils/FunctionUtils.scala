/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

object FunctionUtils {

  def chain[T](first: (T) ⇒ T, second: (T) ⇒ T): (T) ⇒ T = {
    { x ⇒
      second(first(x))
    }
  }
}
