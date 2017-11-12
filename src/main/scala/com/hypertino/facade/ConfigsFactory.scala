/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade

import java.io.{File, FileNotFoundException}

import com.hypertino.facade.raml.{RamlConfigException, RamlConfiguration, RamlConfigurationBuilder}
import com.typesafe.config.Config
import org.raml.v2.api.RamlModelBuilder
import scaldi.Injector

// todo: rename this
object ConfigsFactory {
  def ramlConfig(appConfig: Config)(implicit inj: Injector): RamlConfiguration = {
    ramlFilesPaths(appConfig).map { ramlConfigPath ⇒
      val path = resourceFile(ramlConfigPath)
      val apiFile = new File(path)
      if (!apiFile.exists()) {
        throw new FileNotFoundException(s"File ${apiFile.getAbsolutePath} doesn't exists")
      }
      val buildApi = new RamlModelBuilder().buildApi(path)
      val api = buildApi.getApiV10
      if (api == null) {
        import scala.collection.JavaConverters._
        throw RamlConfigException(buildApi.getValidationResults.asScala.mkString("\n"))
      }
      RamlConfigurationBuilder(api).build
    }.foldLeft(RamlConfiguration("", Map.empty, Map.empty)) { (set: RamlConfiguration, i: RamlConfiguration) ⇒
      // todo: don't merge RAML configs !!!!
      RamlConfiguration(mergeBaseUri(set.baseUri, i.baseUri), set.resourcesByPattern ++ i.resourcesByPattern, set.dataTypes ++ i.dataTypes)
    }
  }

  private def mergeBaseUri(left: String, right: String): String = {
    if (right.startsWith("http://") || right.startsWith("https://")) {
      if (left.isEmpty) {
        right
      }
      else {
        if (left != right) {
          throw new IllegalArgumentException(s"Two facade RAML files with inconsistent baseUri: $left and $right.")
        }
        else {
          left
        }
      }
    }
    else {
      left
    }
  }

  private def ramlFilesPaths(config: Config): Seq[String] = {
    import scala.collection.JavaConversions._
    val s = System.getProperty(FacadeConfigPaths.RAML_FILES)
    if (s != null && s.nonEmpty) {
      s.split(java.io.File.pathSeparator).map(_.trim)
    }
    else {
      config.getStringList(FacadeConfigPaths.RAML_FILES).toSeq
    }
  }

  private def resourceFile(s: String): String = {
    val prefix = "resources://"
    if (s.startsWith(prefix)) {
      val resourcePath = s.substring(prefix.length)
      val r = Thread.currentThread().getContextClassLoader.getResource(resourcePath)
      if (r != null)
        r.getFile
      else
        resourcePath
    }
    else {
      s
    }
  }
}

object FacadeConfigPaths {
  final val ROOT = "hyperfacade."
  final val LOGGERS = ROOT + "loggers"
  final val RAML_FILES = ROOT + "raml.files"
  final val INJECT_MODULES = ROOT + "inject-modules"
  final val HTTP = ROOT + "http-transport"
  final val SHUTDOWN_TIMEOUT = ROOT + "shutdown-timeout"
  final val MAX_SUBSCRIPTION_TRIES = ROOT + "max-subscription-tries"
  final val REWRITE_COUNT_LIMIT = ROOT + "rewrite-count-limit"
  final val FEED_MAX_STASHED_EVENTS_COUNT = ROOT + "feed-max-stashed-events-count"
}
