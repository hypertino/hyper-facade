package com.hypertino.facade.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL

case class RamlConfiguration(baseUri: String, resourcesByPattern: Map[String, ResourceConfig]) {
  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method).map(foundTrait ⇒ foundTrait.name).distinct
  }

  def resourceHRL(requestHRL: HRL, method: String): Option[HRL] = {
    //todo: lookup in map instead of sequence!
    ResourcePatternMatcher.matchResource(requestHRL.location, resourcesByPattern.keySet).map(h ⇒
      h.copy(
        query = h.query + requestHRL.query
      )
    )
  }

  private def traits(uriPattern: String, method: String): Seq[Trait] = {
    resourcesByPattern.get(uriPattern) match {
      case Some(configuration) ⇒
        val traits = configuration.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }
}

case class ResourceConfig(
                           traits: Traits,
                           annotations: Seq[RamlAnnotation],
                           methods: Map[Method, RamlResourceMethodConfig],
                           filters: SimpleFilterChain
                         )

case class RamlResourceMethodConfig(method: Method,
                                    annotations: Seq[RamlAnnotation],
                                    requests: RamlRequests,
                                    responses: Map[Int, RamlResponses],
                                    methodFilters: SimpleFilterChain)

case class RamlRequests(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])
case class RamlResponses(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])
case class RamlContentTypeConfig(headers: Seq[Header], typeDefinition: TypeDefinition, filters: SimpleFilterChain)

case class Traits(commonTraits: Seq[Trait], methodSpecificTraits: Map[Method, Seq[Trait]])
case class Trait(name: String, parameters: Map[String, String])
object Trait {
  def apply(name: String): Trait = {
    Trait(name, Map())
  }
}

case class Method(name: String)
object Method {
  val POST = "post"
  val GET = "get"
  val PUT = "put"
  val DELETE = "delete"
  val PATCH = "patch"
}

case class ContentType(mediaType: String)

case class Header(name: String)

object DataType {
  val DEFAULT_TYPE_NAME = "string"
}

case class TypeDefinition(typeName: String, parentTypeName: Option[String], annotations: Seq[RamlAnnotation], fields: Seq[Field])
object TypeDefinition {
  def apply(): TypeDefinition = {
    TypeDefinition(DataType.DEFAULT_TYPE_NAME, None, Seq.empty, Seq.empty)
  }
}

case class Field(name: String, typeName: String, annotations: Seq[RamlAnnotation])
