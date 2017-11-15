/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.filters.annotated

import com.hypertino.binders.annotations.fieldName
import com.hypertino.binders.value.Value
import com.hypertino.facade.filter.model._
import com.hypertino.facade.filter.parser.PreparedExpression
import com.hypertino.facade.model.ErrorCode
import com.hypertino.facade.raml.RamlFieldAnnotation
import com.hypertino.hyperbus.model.{ErrorBody, HeadersBuilder, StandardResponse, Status}
import monix.eval.Task

case class ErrorResponseFieldAnnotation(
                                @fieldName("if") predicate: Option[PreparedExpression],
                                stages: Set[FieldFilterStage] = Set(FieldFilterStageRequest),
                                _name: String = ""
                              ) extends RamlFieldAnnotation {
  def name: String = _name
}

// todo: add statusCode to support different replies
class ErrorResponseFieldFilter(fieldName: String, typeName: String, errorStatusCode: Int) extends FieldFilter {
  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (context.value.isDefined) Task.raiseError {
      StandardResponse(
        ErrorBody(ErrorCode.FIELD_IS_PROTECTED, Some(s"You can't set field `$fieldName`")),
        new HeadersBuilder
          withStatusCode errorStatusCode
          withContext context.requestContext
          responseHeaders()
      ).asInstanceOf[Throwable]
    } else Task.now {
      None
    }
  }
}

class ErrorResponseFieldFilterFactory extends RamlFieldFilterFactory {
  def resolveErrorStatusCode(name: String): Int = Status.nameToStatusCode.getOrElse(name, Status.INTERNAL_SERVER_ERROR)

  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlFieldAnnotation): FieldFilter = new ErrorResponseFieldFilter(
    fieldName, typeName, resolveErrorStatusCode(annotation.name)
  )

  override def createRamlAnnotation(name: String, value: Value): RamlFieldAnnotation = {
    value.to[ErrorResponseFieldAnnotation].copy(_name=name)
  }
}