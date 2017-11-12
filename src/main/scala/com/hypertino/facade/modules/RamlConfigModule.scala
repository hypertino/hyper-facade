/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.modules

import com.hypertino.facade.ConfigsFactory
import com.hypertino.facade.raml.RamlConfiguration
import com.typesafe.config.Config
import scaldi.Module

class RamlConfigModule extends Module {
  bind[RamlConfiguration] identifiedBy 'raml to ConfigsFactory.ramlConfig(inject[Config])
}
