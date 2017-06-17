package com.hypertino.facade.raml

import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.raml.utils.UriParser

import scala.collection.immutable.SortedMap

// todo: naming uri vs hrl
case class IndexKey(hrl: HRL, method: Option[Method])

case class RewriteIndex(inverted: Map[IndexKey, String], forward: Map[IndexKey, String]) {
  def findRewriteForward(hrl: HRL, requestMethod: Option[String]): Option[HRL] = {
    findRewrite(hrl, requestMethod, forward)
  }

  def findRewriteBackward(hrl: HRL, requestMethod: Option[String]): Option[HRL] = {
    findRewrite(hrl, requestMethod, inverted)
  }

  private def findRewrite(hrl: HRL, requestMethod: Option[String], index: Map[IndexKey, String]): Option[HRL] = {
    val method = requestMethod.map(m ⇒ Method(m))
    findMostSpecificRewriteRule(index, method, hrl)
  }

  private def findMostSpecificRewriteRule(index: Map[IndexKey, String], method: Option[Method], originalHRL: HRL): Option[HRL] = {
    exactMatch(index, method, originalHRL) orElse
      patternMatch(index, method, originalHRL) match {
      case Some(matchedHRL) ⇒
        val newQuery = originalHRL.query + matchedHRL.query
        Some(matchedHRL.copy(
          query = newQuery
        ))
      case None ⇒
        None
    }
  }

  private def exactMatch(index: Map[IndexKey, String], method: Option[Method], originalHRL: HRL): Option[HRL] = {
    index.get(IndexKey(originalHRL, method)) orElse
      index.get(IndexKey(originalHRL, None)) match {
      case Some(uri) ⇒
        Some(HRL(uri))
      case None ⇒ None
    }
  }

  private def patternMatch(index: Map[IndexKey, String], method: Option[Method], originalHRL: HRL): Option[HRL] = {
    index
      .iterator
      .map(i ⇒ ResourcePatternMatcher.matchResource(i._1.hrl.location, originalHRL.location))
      .find(_.nonEmpty)
      .flatten
  }
}

object RewriteIndex {

  implicit object UriTemplateOrdering extends Ordering[IndexKey] {
    override def compare(left: IndexKey, right: IndexKey): Int = {
      if (left.method.isDefined) {
        if (right.method.isDefined)
          compareUriTemplates(left.hrl.location, right.hrl.location)
        else
          1
      } else if (right.method.isDefined) {
        -1
      } else {
        compareUriTemplates(left.hrl.location, right.hrl.location)
      }
    }

    def compareUriTemplates(left: String, right: String): Int = {
      val leftTokens = UriParser.tokens(left).length
      val rightTokens = UriParser.tokens(right).length
      val uriLengthDiff = leftTokens - rightTokens
      if (uriLengthDiff != 0)
        uriLengthDiff
      else
        left.compareTo(right)
    }
  }

  def apply(): RewriteIndex = {
    RewriteIndex(SortedMap.empty[IndexKey, String], SortedMap.empty[IndexKey, String])
  }
}
