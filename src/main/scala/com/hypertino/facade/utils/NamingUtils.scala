package com.hypertino.facade.utils

import com.hypertino.binders.naming._

object NamingUtils {
  val httpToFacade = new HyphenCaseToCamelCaseConverter
  val facadeToHttp = new CamelCaseToHyphenCaseConverter
}
