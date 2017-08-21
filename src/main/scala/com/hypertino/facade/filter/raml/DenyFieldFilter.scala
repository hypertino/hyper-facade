package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Value}
import com.hypertino.facade.filter.model.{FieldFilter, FieldFilterContext, RamlFieldFilterFactory}
import com.hypertino.facade.raml.RamlAnnotation
import com.hypertino.hyperbus.model.{ErrorBody, Forbidden}
import com.hypertino.parser.ast.Identifier
import com.hypertino.parser.{HParser, ast}
import monix.eval.Task

// todo: add statusCode to support different replies
class DenyFieldFilter(fieldName: String, typeName: String) extends FieldFilter {
//  val identifier: ast.Identifier = {
//    HParser(fieldName) match {
//      case i: Identifier ⇒ i
//      case other ⇒ Identifier(Seq(fieldName))
//    }
//  }
  def apply(context: FieldFilterContext): Task[Option[Value]] = {
    if (context.value.isDefined) Task.raiseError {
      implicit val mcx = context.requestContext
      Forbidden(ErrorBody("field-is-protected", Some(s"You can't set field `$fieldName`")))
    } else Task.now {
      None
    }
  }
}

class DenyFieldFilterFactory extends RamlFieldFilterFactory {
  def createFieldFilter(fieldName: String, typeName: String, annotation: RamlAnnotation): FieldFilter = new DenyFieldFilter(
    fieldName, typeName
  )
}