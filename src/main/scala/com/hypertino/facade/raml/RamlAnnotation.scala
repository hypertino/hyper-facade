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
  final val SET = "set"
  final val REWRITE = "rewrite"
  final val FORWARD = "forward"
  final val DENY = "deny"
  final val REMOVE = "remove"
  final val AUTHORIZE = "authorize"
  final val FETCH = "fetch"
  final val EXTRACT_ITEM = "extract_item"
  final val CONTEXT_FETCH = "context_fetch"

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
    val propMap = properties.map(property ⇒ property.name() → recursiveProperty(property)).toMap
    def propMapString(key: String, defaultValue: String): String = propMap.get(key).map(_.toString).getOrElse(defaultValue)
    def predicate = propMap.get("if").map(_.toString)
    def predicateExpression = predicate.map(PreparedExpression.apply)
    def locationExpression = PreparedExpression(propMap("location").toString)
    def queryExpressionMap = propMap.getOrElse("query", Null).toMap.map(kv ⇒ kv._1 → PreparedExpression(kv._2.toString)).toMap
    def stages(default: Seq[String]): Set[FieldFilterStage] = propMap.getOrElse("stages", Lst(default.map(Text))).toSeq.map(l ⇒ FieldFilterStage.apply(l.toString)).toSet
    def defaultStatuses(default: Int): Set[Int] = propMap.getOrElse("default_statuses", Lst.from(default)).toSeq.map(_.toInt).toSet

    name match {
      case DENY ⇒
        DenyAnnotation(predicate = predicateExpression, stages = stages(Seq(FieldFilterStageRequest.stringValue)))
      case REMOVE ⇒
        RemoveAnnotation(predicate = predicateExpression, stages = stages(Seq(FieldFilterStageResponse.stringValue,FieldFilterStageEvent.stringValue)))
      case SET ⇒
        SetAnnotation(predicate = predicateExpression,
          source = PreparedExpression(propMap("source").toString),
          target = propMap.get("target").map(_.toString),
          stages = stages(Seq(FieldFilterStageRequest.stringValue)))
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
          defaultStatuses = defaultStatuses(404),
          selector = propMap.get("selector").map(o ⇒ PreparedExpression(o.toString)),
          defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString)))
      case FETCH ⇒
        FetchAnnotation(predicate = predicateExpression,
            location = locationExpression,
            query = queryExpressionMap,
            expects = propMapString("expects", "document"),
            onError = propMapString("on_error", "fail"),
            defaultStatuses = defaultStatuses(404),
            defaultValue = propMap.get("default").map(o ⇒ PreparedExpression(o.toString)),
            stages = stages(Seq(FieldFilterStageResponse.stringValue,FieldFilterStageEvent.stringValue)),
            selector = propMap.get("selector").map(o ⇒ PreparedExpression(o.toString)),
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

trait FetchAnnotationBase {
  def name: String
  def predicate: Option[PreparedExpression]
  def location: PreparedExpression
  def query: Map[String, PreparedExpression]
  def expects: String //todo: this should be enum
  def onError: String //todo: this should be enum
  def defaultStatuses: Set[Int]
  def selector: Option[PreparedExpression]
  def defaultValue: Option[PreparedExpression]
}

case class ContextFetchAnnotation(name: String = RamlAnnotation.CONTEXT_FETCH,
                                  predicate: Option[PreparedExpression],
                                  target: String,
                                  location: PreparedExpression,
                                  query: Map[String, PreparedExpression],
                                  expects: String, //todo: this should be enum
                                  onError: String, //todo: this should be enum
                                  defaultStatuses: Set[Int],
                                  selector: Option[PreparedExpression],
                                  defaultValue: Option[PreparedExpression]
                                 ) extends RamlAnnotation with FetchAnnotationBase

// todo: split DenyAnnotation to DenyFilterAnnotation and non-field
case class DenyAnnotation(name: String = RamlAnnotation.DENY,
                          predicate: Option[PreparedExpression],
                          stages: Set[FieldFilterStage]
                         ) extends RamlFieldAnnotation

case class RemoveAnnotation(name: String = RamlAnnotation.REMOVE,
                            predicate: Option[PreparedExpression],
                            stages: Set[FieldFilterStage]
                           ) extends RamlFieldAnnotation

// todo: split SetAnnotation to SetFieldAnnotation and non-field
case class SetAnnotation(name: String = RamlAnnotation.SET,
                         predicate: Option[PreparedExpression],
                         source: PreparedExpression,
                         target: Option[String],
                         stages: Set[FieldFilterStage]
                        ) extends RamlFieldAnnotation


case class FetchAnnotation(name: String = RamlAnnotation.FETCH,
                           predicate: Option[PreparedExpression],
                           location: PreparedExpression,
                           query: Map[String, PreparedExpression],
                           expects: String, //todo: this should be enum
                           onError: String, //todo: this should be enum
                           defaultStatuses: Set[Int],
                           defaultValue: Option[PreparedExpression],
                           stages: Set[FieldFilterStage],
                           selector: Option[PreparedExpression],
                           always: Boolean
                          ) extends RamlFieldAnnotation with FetchAnnotationBase

case class AuthorizeAnnotation(name: String = RamlAnnotation.AUTHORIZE,
                               predicate: Option[PreparedExpression]) extends RamlAnnotation

case class RegularAnnotation(name: String, predicate: Option[PreparedExpression],
                             properties: Map[String, String] = Map.empty) extends RamlAnnotation