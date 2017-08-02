package com.hypertino.facade.raml

import com.hypertino.facade.filter.parser.PreparedExpression
import org.raml.v2.api.model.v10.datamodel.TypeInstanceProperty

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

// todo: make this injectable !!

object RamlAnnotation {
  val SET = "set"
  val REWRITE = "rewrite"
  val DENY = "deny"
  val REMOVE = "remove"
  val AUTHORIZE = "authorize"
  val FETCH = "fetch"

  def apply(name: String, properties: Seq[TypeInstanceProperty]): RamlAnnotation = {
    val propMap = properties.map(property ⇒ property.name() → property.value.value()).toMap
    val predicate = propMap.get("if").map(_.toString)
    val preparedExpression = predicate.map(PreparedExpression.apply)
    name match {
      case DENY ⇒
        DenyAnnotation(predicate = preparedExpression)
      case REMOVE ⇒
        RemoveAnnotation(predicate = preparedExpression)
      case SET ⇒
        SetAnnotation(predicate = preparedExpression, source = PreparedExpression(propMap("source").toString))
      case REWRITE ⇒
        RewriteAnnotation(predicate = preparedExpression, uri = propMap("uri").toString)
      case FETCH ⇒
        FetchAnnotation(predicate = preparedExpression,
            source = PreparedExpression(propMap("source").toString),
            onError = propMap.get("on_error").map(_.toString).getOrElse("fail"),
            defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString))
          )
      case annotationName ⇒
        RegularAnnotation(annotationName, preparedExpression, (propMap - "if").map(kv ⇒ kv._1 → kv._2.toString))
    }
  }
}

case class RewriteAnnotation(name: String = RamlAnnotation.REWRITE,
                             predicate: Option[PreparedExpression],
                             uri: String) extends RamlAnnotation

case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                         predicate: Option[PreparedExpression]) extends RamlAnnotation

case class RemoveAnnotation(name: String = RamlAnnotation.REMOVE,
                            predicate: Option[PreparedExpression]) extends RamlAnnotation

case class SetAnnotation(name: String = RamlAnnotation.SET,
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression
                        ) extends RamlAnnotation


case class FetchAnnotation(name: String = RamlAnnotation.FETCH,
                           predicate: Option[PreparedExpression],
                           source: PreparedExpression,
                           onError: String,
                           defaultValue: Option[PreparedExpression]
                          ) extends RamlAnnotation

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[PreparedExpression]) extends RamlAnnotation
case class RegularAnnotation(name: String, predicate: Option[PreparedExpression], properties: Map[String, String] = Map.empty) extends RamlAnnotation