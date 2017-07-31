package com.hypertino.facade.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL
import com.hypertino.parser.{HParser, ast}
import com.hypertino.parser.ast.Identifier

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
case class RamlContentTypeConfig(headers: Seq[Header], typeDefinition: TypeDefinition, filterChain: SimpleFilterChain)

case class Traits(commonTraits: Seq[Trait], methodSpecificTraits: Map[Method, Seq[Trait]])
case class Trait(name: String, parameters: Map[String, String])
object Trait {
  def apply(name: String): Trait = {
    Trait(name, Map())
  }
}

case class Method(name: String)

case class ContentType(mediaType: String)

case class Header(name: String)

object DataType {
  val DEFAULT_TYPE_NAME = "string"
}

case class TypeDefinition(typeName: String,
                          parentTypeName: Option[String],
                          annotations: Seq[RamlAnnotation],
                          fields: Seq[Field],
                          isCollection: Boolean)
object TypeDefinition {
  val empty = TypeDefinition(DataType.DEFAULT_TYPE_NAME, None, Seq.empty, Seq.empty, isCollection = false)
}

case class Field(name: String, typeName: String, annotations: Seq[RamlAnnotation]) {
  val identifier: ast.Identifier = {
    HParser(name) match {
      case i: Identifier ⇒ i
      case other ⇒ Identifier(Seq(name))
    }
  }
}
