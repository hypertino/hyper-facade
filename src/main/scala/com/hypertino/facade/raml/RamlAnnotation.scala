package com.hypertino.facade.raml

import com.hypertino.binders.value.{False, Lst, Null, Obj, Text, Value}
import com.hypertino.facade.filter.model.{FieldFilterStage, FieldFilterStageEvent, FieldFilterStageRequest, FieldFilterStageResponse}
import com.hypertino.facade.filter.parser.PreparedExpression
import org.raml.v2.api.model.v10.datamodel.{TypeInstance, TypeInstanceProperty}

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

trait RamlFieldAnnotation extends RamlAnnotation {
  def stages: Set[FieldFilterStage]
}
// todo: make this injectable !!

object RamlAnnotation {
  val SET = "set"
  val REWRITE = "rewrite"
  val FORWARD = "forward"
  val DENY = "deny"
  val REMOVE = "remove"
  val AUTHORIZE = "authorize"
  val FETCH = "fetch"
  val EXTRACT_ITEM = "extract_item"
  val CONTEXT_FETCH = "context_fetch"

  import scala.collection.JavaConverters._

  private def recursiveProperty(instance: TypeInstance): Value = {
    if (instance.isScalar) {
      Text(instance.value().toString)
    } else {

      Obj(
        instance.properties().asScala.map { kv ⇒
          kv.name() → recursiveProperty(kv)
        }.toMap
      )
    }
  }

  private def recursiveProperty(instance: TypeInstanceProperty): Value = {
    if (instance.isArray) {
      Lst(
        instance.values().asScala.map(recursiveProperty)
      )
    } else {
      recursiveProperty(instance.value())
    }
  }

  def apply(name: String, properties: Seq[TypeInstanceProperty]): RamlAnnotation = {
    val propMap = properties.map(property ⇒ property.name() → recursiveProperty(property.value())).toMap
    def propMapString(key: String, defaultValue: String): String = propMap.get(key).map(_.toString).getOrElse(defaultValue)
    def predicate = propMap.get("if").map(_.toString)
    def predicateExpression = predicate.map(PreparedExpression.apply)
    def locationExpression = PreparedExpression(propMap("location").toString)
    def queryExpressionMap = propMap.getOrElse("query", Null).toMap.map(kv ⇒ kv._1 → PreparedExpression(kv._2.toString)).toMap
    def stages(default: String): Set[FieldFilterStage] = propMapString("stages", default).split(",").map(FieldFilterStage.apply).toSet

    name match {
      case DENY ⇒
        DenyAnnotation(predicate = predicateExpression, stages = stages(FieldFilterStageRequest.stringValue))
      case REMOVE ⇒
        RemoveAnnotation(predicate = predicateExpression, stages = stages(s"${FieldFilterStageResponse.stringValue},${FieldFilterStageEvent.stringValue}"))
      case SET ⇒
        SetAnnotation(predicate = predicateExpression, source = PreparedExpression(propMap("source").toString),
          stages = stages(FieldFilterStageRequest.stringValue))
      case REWRITE ⇒
        RewriteAnnotation(predicate = predicateExpression,
          location = propMap("location").toString,
          query = propMap.getOrElse("query", Null)
        )
      case FORWARD ⇒
        ForwardAnnotation(predicate = predicateExpression,
          location = locationExpression,
          query = queryExpressionMap,
          method = propMap.get("method").map(o ⇒ PreparedExpression(o.toString))
        )
      case EXTRACT_ITEM ⇒
        ExtractItemAnnotation(predicate = predicateExpression) // todo: add single, head, tail, index...
      case CONTEXT_FETCH ⇒
        ContextFetchAnnotation(predicate = predicateExpression,
          target = propMapString("target", ""), // todo: fail if target is empty
          location = locationExpression,
          query = queryExpressionMap,
          expects = propMapString("expects", "document"),
          onError = propMapString("on_error", "fail"),
          defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString)))
      case FETCH ⇒
        FetchAnnotation(predicate = predicateExpression,
            location = locationExpression,
            query = queryExpressionMap,
            expects = propMapString("expects", "document"),
            onError = propMapString("on_error", "fail"),
            defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString)),
            stages = stages(s"${FieldFilterStageResponse.stringValue},${FieldFilterStageEvent.stringValue}"),
            always = propMap.getOrElse("always", False).toBoolean
          )
      case annotationName ⇒
        RegularAnnotation(annotationName, predicateExpression, (propMap - "if").map(kv ⇒ kv._1 → kv._2.toString))
    }
  }
}

case class RewriteAnnotation(name: String = RamlAnnotation.REWRITE,
                             predicate: Option[PreparedExpression],
                             location: String,
                             query: Value) extends RamlAnnotation

case class ForwardAnnotation(name: String = RamlAnnotation.FORWARD,
                             predicate: Option[PreparedExpression],
                             location: PreparedExpression,
                             query: Map[String, PreparedExpression],
                             method: Option[PreparedExpression]
                            ) extends RamlAnnotation

case class ExtractItemAnnotation(name: String = RamlAnnotation.EXTRACT_ITEM,
                                 predicate: Option[PreparedExpression]
                            ) extends RamlAnnotation

case class ContextFetchAnnotation(name: String = RamlAnnotation.CONTEXT_FETCH,
                                  predicate: Option[PreparedExpression],
                                  target: String,
                                  location: PreparedExpression,
                                  query: Map[String, PreparedExpression],
                                  expects: String, //todo: this should be enum
                                  onError: String, //todo: this should be enum
                                  defaultValue: Option[PreparedExpression]
                                 ) extends RamlAnnotation

// todo: split DenyAnnotation to DenyFilterAnnotation and non-filter
case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                          predicate: Option[PreparedExpression],
                          stages: Set[FieldFilterStage]
                         ) extends RamlFieldAnnotation

case class RemoveAnnotation(name: String = RamlAnnotation.REMOVE,
                            predicate: Option[PreparedExpression],
                            stages: Set[FieldFilterStage]
                           ) extends RamlFieldAnnotation

case class SetAnnotation(name: String = RamlAnnotation.SET,
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression,
                         stages: Set[FieldFilterStage]
                        ) extends RamlFieldAnnotation


case class FetchAnnotation(name: String = RamlAnnotation.FETCH,
                           predicate: Option[PreparedExpression],
                           location: PreparedExpression,
                           query: Map[String, PreparedExpression],
                           expects: String, //todo: this should be enum
                           onError: String, //todo: this should be enum
                           defaultValue: Option[PreparedExpression],
                           stages: Set[FieldFilterStage],
                           always: Boolean
                          ) extends RamlFieldAnnotation

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[PreparedExpression]) extends RamlAnnotation

case class RegularAnnotation(name: String, predicate: Option[PreparedExpression],
                             properties: Map[String, String] = Map.empty) extends RamlAnnotation