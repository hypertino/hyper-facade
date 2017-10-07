package com.hypertino.facade.raml

import com.hypertino.facade.filter.model.FieldFilterStage
import com.hypertino.facade.filter.parser.PreparedExpression

trait RamlAnnotation {
  def name: String
  def predicate: Option[PreparedExpression]
}

trait RamlFieldAnnotation extends RamlAnnotation {
  def stages: Set[FieldFilterStage]
}
