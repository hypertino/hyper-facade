package eu.inn.facade.model

import eu.inn.facade.filter.chain.Filters
import eu.inn.facade.raml.Field

trait Filter

trait RamlFilterFactory {
  def createFilters(target: RamlTarget): Filters
}

sealed trait RamlTarget
case class TargetResource(uri: String) extends RamlTarget
case class TargetMethod(uri: String, method: String) extends RamlTarget
//case class TargetResponse(uri: String, code: Int) extends RamlTarget
//RequestBody
//ResponseBody
// todo: inner fields!
case class TargetFields(typeName: String, fields: Seq[Field]) extends RamlTarget