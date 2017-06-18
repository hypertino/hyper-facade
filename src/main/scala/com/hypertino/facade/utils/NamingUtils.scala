package com.hypertino.facade.utils

import com.hypertino.inflector.naming.{CamelCaseToHyphenCaseConverter, HyphenCaseToCamelCaseConverter}

object NamingUtils {
  val httpToFacade = HyphenCaseToCamelCaseConverter
  val facadeToHttp = CamelCaseToHyphenCaseConverter
}
