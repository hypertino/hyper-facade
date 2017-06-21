package com.hypertino.facade.raml

import com.hypertino.facade.FacadeConfigPaths
import com.typesafe.config.Config
import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL

class RamlConfigurationReader(ramlConfiguration: RamlConfiguration, config: Config) {

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method).map(foundTrait ⇒ foundTrait.name).distinct
  }

  def resourceHRL(requestHRL: HRL, method: String): HRL = {
    //todo: lookup in map instead of sequence!
    //val formattedUri = Uri(requestUri.formatted)

    val hrlWithoutQuery = ResourcePatternMatcher.matchResource(requestHRL.location, ramlConfiguration.resourcesByPattern.keySet) match {
      case Some(h) ⇒ h
      case _ ⇒
        if (config.getBoolean(FacadeConfigPaths.RAML_STRICT_CONFIG)) {
          throw RamlStrictConfigException(s"resource '$requestHRL' with method '$method' is not configured in RAML configuration")
        }
        else {
          requestHRL
        }
    }

    val hrl = hrlWithoutQuery.copy(
      query = hrlWithoutQuery.query + requestHRL.query
    )

    hrl
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    ramlConfiguration.resourcesByPattern.get(uriPattern) match {
      case Some(configuration) ⇒
        val traits = configuration.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }
}
