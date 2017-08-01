package com.hypertino.facade.raml

import com.hypertino.facade.filter.chain.SimpleFilterChain
import com.hypertino.facade.filter.model.RamlFieldFilterFactory
import com.hypertino.facade.filter.raml.FieldFilterAdapterFactory
import com.hypertino.hyperbus.serialization.JsonContentTypeConverter
import com.hypertino.inflector.naming.CamelCaseToDashCaseConverter
import org.raml.v2.api.model.v10.api.Api
import org.raml.v2.api.model.v10.bodies.Response
import org.raml.v2.api.model.v10.common.Annotable
import org.raml.v2.api.model.v10.datamodel.{ObjectTypeDeclaration, TypeDeclaration}
import org.raml.v2.api.model.v10.methods
import org.raml.v2.api.model.v10.methods.TraitRef
import org.raml.v2.api.model.v10.resources.Resource
import org.slf4j.LoggerFactory
import scaldi.{Injectable, Injector}

import scala.collection.JavaConversions._

class RamlConfigurationBuilder(val api: Api)(implicit inj: Injector) extends Injectable {
  private val log = LoggerFactory.getLogger(getClass)
  private val baseUri = api.baseUri().value
  private lazy val dataTypes: Map[String, TypeDefinition] = parseTypeDefinitions

  def build: RamlConfiguration = {
    val resourcesByUriAcc = Map.newBuilder[String, ResourceConfig]
    api.resources()
      .foreach { resource ⇒
        val currentRelativeUri = resource.relativeUri().value()
        val resourceData = parseResource(currentRelativeUri, resource, Seq.empty)
        resourcesByUriAcc ++= resourceData
    }
    val resourceMapWithFilters = new RamlConfigFiltersInjector(resourcesByUriAcc.result()).withResourceFilters()
    val prefix = if (baseUri.startsWith("http://") || baseUri.startsWith("https://")) {
      ""
    }
    else {
      baseUri
    }
    RamlConfiguration(baseUri, resourceMapWithFilters.map(kv ⇒ (prefix + kv._1, kv._2)), dataTypes)
  }

  private def parseTypeDefinitions: Map[String, TypeDefinition] = {
    val typeDefinitions = api.types().map { ramlTypeRaw ⇒
      val ramlType = ramlTypeRaw.asInstanceOf[ObjectTypeDeclaration]
      val fields = ramlType.properties().map(parseField).toMap
      val typeName = ramlType.name
      val parentTypeName = ramlType.`type`.isEmpty match {
        case true ⇒ None
        case false ⇒ Some(ramlType.`type`)
      }
      val annotations = extractAnnotations(ramlType)
      typeName → TypeDefinition(typeName, parentTypeName, annotations, fields, false)
    }.toMap

    //withFlattenedFields(typeDefinitions)
    typeDefinitions
  }

  private def getTypeDefinition(fieldType: String, typeDefinitions: Map[String, TypeDefinition]): Option[TypeDefinition] ={
    if (fieldType.endsWith("[]")) {
      val s = fieldType.substring(0, fieldType.length-2)
      typeDefinitions.get(s).map(_.copy(isCollection = true))
    }
    else {
      typeDefinitions.get(fieldType)
    }
  }

  private def parseField(ramlField: TypeDeclaration): (String, Field) = {
    val name = ramlField.name
    val typeName = ramlField.`type`
    val annotations = extractAnnotations(ramlField).map { annotation ⇒
      new FieldAnnotationWithFilter(annotation, name, typeName)
    }
    name → Field(name, typeName, annotations)
  }

  private def parseResource(currentUri: String, resource: Resource, parentAnnotations: Seq[RamlAnnotation]): (Map[String, ResourceConfig]) = {
    val traits = extractResourceTraits(resource) // todo: eliminate?

    val adjustedParentAnnotations = adjustParentAnnotations(resource.relativeUri.value(), parentAnnotations)
    val resourceAnnotations = adjustedParentAnnotations ++ extractAnnotations(resource)
    val resourceMethods = extractResourceMethods(currentUri, resource)

    val resourceConfig = ResourceConfig(traits, resourceAnnotations, resourceMethods, SimpleFilterChain.empty)

    val configuration = Map.newBuilder[String, ResourceConfig]
    configuration += (currentUri → resourceConfig)
    resource.resources().foldLeft(configuration) { (configuration, childResource) ⇒
      val childResourceRelativeUri = childResource.relativeUri().value()
      val resourceData = parseResource(currentUri + childResourceRelativeUri, childResource, resourceAnnotations)
      configuration ++= resourceData
    }
    configuration.result()
  }

  private def extractResourceMethods(currentUri: String, resource: Resource): Map[Method, RamlResourceMethodConfig] = {
    val builder = Map.newBuilder[Method, RamlResourceMethodConfig]
    resource.methods.foreach { ramlMethod ⇒
      builder += Method(ramlMethod.method) → extractResourceMethod(currentUri, ramlMethod, resource)
    }
    builder.result()
  }

  private def extractResourceMethod(currentUri: String, ramlMethod: methods.Method, resource: Resource): RamlResourceMethodConfig = {
    val methodAnnotations = extractAnnotations(ramlMethod)
    val method = Method(ramlMethod.method())

    val ramlRequests = RamlRequests(extractRamlContentTypes(RamlRequestResponseWrapper(ramlMethod)))

    val ramlResponses = Map.newBuilder[Int, RamlResponses]
    ramlMethod.responses.foreach { ramlResponse ⇒
      val statusCode = ramlResponse.code.value.toInt
      val responseRamlContentTypes = extractRamlContentTypes(RamlRequestResponseWrapper(ramlResponse))
      ramlResponses += statusCode → RamlResponses(responseRamlContentTypes)
    }

    RamlResourceMethodConfig(method, methodAnnotations, ramlRequests, ramlResponses.result(), SimpleFilterChain.empty)
  }

  private def adjustParentAnnotations(childResourceRelativeUri: String, parentAnnotations: Seq[RamlAnnotation]): Seq[RamlAnnotation] = {
    val adjustedAnnotations = Seq.newBuilder[RamlAnnotation]
    parentAnnotations.foreach {
      case RewriteAnnotation(name, predicate, uri) ⇒
        val adjustedRewrittenUri = uri + childResourceRelativeUri
        adjustedAnnotations += RewriteAnnotation(name, predicate, adjustedRewrittenUri)
      case otherAnn ⇒ adjustedAnnotations += otherAnn
    }
    adjustedAnnotations.result()
  }

  private def typeHaveFieldAnnotations(typeDef: TypeDefinition): Boolean = {
    typeDef.fields.values.exists(_.annotations.nonEmpty) ||
      typeDef.fields.exists { f ⇒
        getTypeDefinition(f._2.typeName, dataTypes) match {
          case Some(t) ⇒ typeHaveFieldAnnotations(t)
          case None ⇒ false
        }
      }
  }

  private def extractRamlContentTypes(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[ContentType], RamlContentTypeConfig] = {
    val headers = ramlReqRspWrapper.headers.foldLeft(Seq.newBuilder[Header]) { (headerList, ramlHeader) ⇒
      headerList += Header(ramlHeader.name())
    }.result()
    val typeNames: Map[Option[String], Option[String]] = getTypeNamesByContentType(ramlReqRspWrapper)

    typeNames.foldLeft(Map.newBuilder[Option[ContentType], RamlContentTypeConfig]) { (ramlContentTypes, typeDefinition) ⇒
      val (contentTypeName, typeName) = typeDefinition
      val contentType = contentTypeName.map(ContentType)

      val ramlContentType = typeName match {
        case Some(name) ⇒ getTypeDefinition(name, dataTypes) match {
          case Some(typeDef) ⇒
            val filterChain = if(typeHaveFieldAnnotations(typeDef)) {
              inject[FieldFilterAdapterFactory].createFilters(typeDef)
            } else {
              SimpleFilterChain.empty
            }
            RamlContentTypeConfig(headers, typeDef, filterChain)

          case None ⇒
            RamlContentTypeConfig(headers, TypeDefinition.empty, SimpleFilterChain.empty)
        }

        case None ⇒
          RamlContentTypeConfig(headers, TypeDefinition.empty, SimpleFilterChain.empty)
      }
      ramlContentTypes += (contentType → ramlContentType)
    }.result()
  }

  private def getTypeNamesByContentType(ramlReqRspWrapper: RamlRequestResponseWrapper): Map[Option[String], Option[String]] = {
    if (ramlReqRspWrapper.body.isEmpty
      || ramlReqRspWrapper.body.get(0).`type`.isEmpty)
      Map(None → None)
    else {
      ramlReqRspWrapper.body.foldLeft(Map.newBuilder[Option[String], Option[String]]) { (typeNames, body) ⇒
        val contentType = Option(body.name).map(_.toLowerCase) match {
          case None | Some("body") | Some("none") | Some("application/json") ⇒
            Some(contentTypeFromTypeName(body.`type`)) // todo: is this ok? test it

          case Some(other) ⇒
            Some(JsonContentTypeConverter.universalJsonContentTypeToSimple(other).toString)
        }
        val typeName = body.`type`
        typeNames += (contentType → Option(typeName))
      }
    }.result()
  }

  private def contentTypeFromTypeName(typeName: String): String = {
    val s = CamelCaseToDashCaseConverter.convert(typeName)
    if (s.endsWith("[]")) {
      s.substring(0, s.length-2) + "-collection"
    }
    else {
      s
    }
  }

  private def extractAnnotations(annotable: Annotable): Seq[RamlAnnotation] = {
    annotable.annotations.map { annotation ⇒
      RamlAnnotation(annotation.annotation.name, annotation.structuredValue.properties)
    }
  }

  private def extractResourceTraits(resource: Resource): Traits = {
    val commonResourceTraits = extractTraits(resource.is())
    val methodSpecificTraits = resource.methods().foldLeft(Map.newBuilder[Method, Seq[Trait]]) { (specificTraits, ramlMethod) ⇒
      val method = Method(ramlMethod.method)
      val methodTraits = extractTraits(ramlMethod.is())
      specificTraits += (method → (methodTraits ++ commonResourceTraits))
    }.result()
    Traits(commonResourceTraits, methodSpecificTraits)
  }

  private def extractTraits(traits: java.util.List[TraitRef]): Seq[Trait] = {
    traits.foldLeft(Seq.newBuilder[Trait]) {
      (accumulator, traitRef) ⇒
        val traitName = traitRef.`trait`().name()
        accumulator += Trait(traitName)
    }.result()
  }
}

object RamlConfigurationBuilder {
  def apply(api: Api)(implicit inj: Injector) = {
    new RamlConfigurationBuilder(api)
  }
}

private[raml] class RamlRequestResponseWrapper(val method: Option[methods.Method], val response: Option[Response]) {

  def body: java.util.List[TypeDeclaration] = {
    var bodyList = Seq[TypeDeclaration]()
    if (method.isDefined) bodyList = bodyList ++ method.get.body
    if (response.isDefined) bodyList = bodyList ++ response.get.body
    bodyList
  }

  def headers: java.util.List[TypeDeclaration] = {
    var bodyList = Seq[TypeDeclaration]()
    if (method.isDefined) bodyList = bodyList ++ method.get.headers
    if (response.isDefined) bodyList = bodyList ++ response.get.headers
    bodyList
  }
}

private[raml] object RamlRequestResponseWrapper {
  def apply(response: Response): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(None, Some(response))
  }

  def apply(method: methods.Method): RamlRequestResponseWrapper = {
    new RamlRequestResponseWrapper(Some(method), None)
  }
}
