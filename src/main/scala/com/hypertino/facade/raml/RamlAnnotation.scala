package com.hypertino.facade.raml

import com.hypertino.facade.filter.parser.PreparedExpression
import org.raml.v2.api.model.v10.datamodel.TypeInstanceProperty

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

object RamlAnnotation {
  val SET = "set"
  val REWRITE = "rewrite"
  val DENY = "deny"
  val AUTHORIZE = "authorize"

  def apply(name: String, properties: Seq[TypeInstanceProperty]): RamlAnnotation = {
    val propMap = properties.map(property ⇒ property.name() → property.value.value().toString).toMap
    val predicate = propMap.get("if")
    val preparedExpression = predicate.map(PreparedExpression.apply)
    name match {
      case DENY ⇒
        DenyAnnotation(predicate = preparedExpression)
      case SET ⇒
        SetAnnotation(predicate = preparedExpression, source = PreparedExpression(propMap("source")))
      case REWRITE ⇒
        RewriteAnnotation(predicate = preparedExpression, uri = propMap("uri"))
      case annotationName ⇒
        RegularAnnotation(annotationName, preparedExpression, propMap - "if")
    }
  }
}

case class RewriteAnnotation(name: String = RamlAnnotation.REWRITE,
                             predicate: Option[PreparedExpression],
                             uri: String) extends RamlAnnotation

case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                         predicate: Option[PreparedExpression]) extends RamlAnnotation

case class SetAnnotation(name: String = RamlAnnotation.SET,
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression
                        ) extends RamlAnnotation

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[PreparedExpression]) extends RamlAnnotation
case class RegularAnnotation(name: String, predicate: Option[PreparedExpression], properties: Map[String, String] = Map.empty) extends RamlAnnotation