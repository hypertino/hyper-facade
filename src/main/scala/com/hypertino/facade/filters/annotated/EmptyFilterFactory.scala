/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.chain.{FilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model.{RamlFilterFactory, RamlFilterTarget}
import com.hypertino.facade.filter.parser.ExpressionEvaluator

class EmptyFilterFactory(protected val predicateEvaluator: ExpressionEvaluator) extends RamlFilterFactory {
  override def createFilters(target: RamlFilterTarget): SimpleFilterChain = {
    FilterChain.empty
  }

  override def createRamlAnnotation(name: String, value: Value) = ???
}
