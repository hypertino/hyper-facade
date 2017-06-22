package com.hypertino.facade

import java.io.{File, FileNotFoundException}

import com.typesafe.config.Config
import com.hypertino.facade.raml.{RamlConfiguration, RamlConfigurationBuilder}
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
      val api = new RamlModelBuilder().buildApi(path).getApiV10
      RamlConfigurationBuilder(api).build
    }.foldLeft(RamlConfiguration("", Map.empty)){ (set: RamlConfiguration, i: RamlConfiguration) ⇒
      RamlConfiguration(mergeBaseUri(set.baseUri, i.baseUri), set.resourcesByPattern ++ i.resourcesByPattern)
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
      s.split(java.io.File.separator).map(_.trim)
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
  val ROOT = "hyper-facade."
  val LOGGERS = ROOT + "loggers"
  val RAML_FILES = ROOT + "raml.files"
  //val RAML_ROOT_PATH_PREFIX = ROOT + "raml.root-path"
  val RAML_STRICT_CONFIG = ROOT + "raml.strict-config" // todo: rename this
  val HYPERBUS_GROUP = ROOT + "hyperbus.group-name"
  val INJECT_MODULES = ROOT + "inject-modules"
  val HTTP = ROOT + "http-transport"
  val SHUTDOWN_TIMEOUT = ROOT + "shutdown-timeout"
  val MAX_SUBSCRIPTION_TRIES = ROOT + "max-subscription-tries"
  val REWRITE_COUNT_LIMIT = ROOT + "rewrite-count-limit"
  val FEED_MAX_STASHED_EVENTS_COUNT = ROOT + "feed-max-stashed-events-count"
}
