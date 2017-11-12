/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.raml

import com.hypertino.facade.filter.model.FieldFilterStage
import com.hypertino.facade.filter.parser.PreparedExpression

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

trait RamlFieldAnnotation extends RamlAnnotation {
  def stages: Set[FieldFilterStage]
}
