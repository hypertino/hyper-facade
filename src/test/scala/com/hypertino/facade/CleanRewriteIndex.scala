/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import com.hypertino.facade.raml.RewriteIndexHolder
import org.scalatest.{BeforeAndAfterAll, Suite}

/**
  * Created by maqdev on 6/12/17.
  */
trait CleanRewriteIndex extends BeforeAndAfterAll {
  this: Suite ⇒

  override def afterAll(): Unit = {
    RewriteIndexHolder.clearIndex()
  }
}
