/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.raml

object RamlTypeUtils {
  def getTypeDefinition(fieldType: String, typeDefinitions: Map[String, TypeDefinition]): Option[TypeDefinition] = {
    if (fieldType.endsWith("[]")) {
      val s = fieldType.substring(0, fieldType.length - 2)
      typeDefinitions.get(s).map(_.copy(isCollection = true))
    }
    else {
      typeDefinitions.get(fieldType)
    }
  }
}
