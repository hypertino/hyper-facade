package com.hypertino.facade.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model.{FieldFilter, RamlFieldFilterFactory}
import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL
import scaldi.{Injectable, Injector}

case class RamlConfiguration(baseUri: String, resourcesByPattern: Map[String, RamlResource], dataTypes: Map[String, TypeDefinition]) {
  val resourcePatternHRLs = resourcesByPattern.keySet.map(HRL(_))

  def traitNames(uriPattern: String, method: String): Seq[String] = {
    traits(uriPattern, method).map(foundTrait ⇒ foundTrait.name).distinct
  }

  def resourceHRL(requestHRL: HRL, method: String): Option[HRL] = {
    //todo: lookup in map instead of sequence!
    ResourcePatternMatcher.matchResource(requestHRL, resourcePatternHRLs).map(h ⇒
      // todo: move this into matchResource?
      h.copy(
        query = h.query + requestHRL.query
      )
    )
  }

  private def traits(uriPattern: String, method: String): Seq[RamlTrait] = {
    resourcesByPattern.get(uriPattern) match {
      case Some(configuration) ⇒
        val traits = configuration.traits
        traits.methodSpecificTraits.getOrElse(Method(method), Seq.empty) ++ traits.commonTraits
      case None ⇒ Seq()
    }
  }

  def getTypeDefinition(fieldType: String): Option[TypeDefinition] = RamlTypeUtils.getTypeDefinition(fieldType, dataTypes)
}

case class RamlResource(
                         traits: RamlTraits,
                         annotations: Seq[RamlAnnotation],
                         methods: Map[Method, RamlResourceMethod],
                         filters: SimpleFilterChain
                         )

case class RamlResourceMethod(method: Method,
                              annotations: Seq[RamlAnnotation],
                              requests: RamlRequests,
                              responses: Map[Int, RamlResponses],
                              methodFilters: SimpleFilterChain,
                              queryParameters: Map[String, Field]
                             )

case class RamlRequests(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])

case class RamlResponses(ramlContentTypes: Map[Option[ContentType], RamlContentTypeConfig])

case class RamlContentTypeConfig(headers: Seq[Header], typeDefinition: TypeDefinition, filterChain: SimpleFilterChain)

case class RamlTraits(commonTraits: Seq[RamlTrait], methodSpecificTraits: Map[Method, Seq[RamlTrait]])

case class RamlTrait(name: String, parameters: Map[String, String])

object RamlTrait {
  def apply(name: String): RamlTrait = {
    RamlTrait(name, Map())
  }
}

case class Method(name: String)

case class ContentType(mediaType: String)

case class Header(name: String)

object DataType {
  final val DEFAULT_TYPE_NAME = "string"
}

case class TypeDefinition(typeName: String,
                          parentTypeName: Option[String],
                          annotations: Seq[RamlAnnotation],
                          fields: Map[String, Field],
                          isCollection: Boolean) // todo: isCollection is a hack here, do something!
object TypeDefinition {
  val empty = TypeDefinition(DataType.DEFAULT_TYPE_NAME, None, Seq.empty, Map.empty, isCollection = false)
}

class FieldAnnotationWithFilter(val annotation: RamlFieldAnnotation, fieldName: String, fieldTypeName: String)
                               (implicit injector: Injector) extends Injectable {

  lazy val filter: FieldFilter = {
    val filterName = annotation.name + "_field"
    inject[RamlFieldFilterFactory](filterName).createFieldFilter(fieldName, fieldTypeName, annotation)
  }
}

case class Field(fieldName: String, fieldTypeName: String, annotations: Seq[FieldAnnotationWithFilter], defaultValue: Option[String])
                (implicit injector: Injector) extends Injectable {

  lazy val typeDefinition: Option[TypeDefinition] = {
    val ramlConfiguration = inject[RamlConfiguration]
    ramlConfiguration.getTypeDefinition(fieldTypeName)
  }
}
