package com.hypertino.facade.raml

object RamlTypeUtils {
  def getTypeDefinition(fieldType: String, typeDefinitions: Map[String, TypeDefinition]): Option[TypeDefinition] ={
    if (fieldType.endsWith("[]")) {
      val s = fieldType.substring(0, fieldType.length-2)
      typeDefinitions.get(s).map(_.copy(isCollection = true))
    }
    else {
      typeDefinitions.get(fieldType)
    }
  }
}
