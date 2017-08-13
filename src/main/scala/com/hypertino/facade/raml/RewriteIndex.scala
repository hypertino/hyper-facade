package com.hypertino.facade.raml

import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.utils.uri.UriPathParser

import scala.collection.immutable.SortedMap

case class IndexKey(hrl: HRL, method: Option[Method])

// todo: !!! this is very inefficient, no actual index is here !!!
case class RewriteIndex(inverted: Map[IndexKey, HRL], forward: Map[IndexKey, HRL]) {
  def findRewriteForward(hrl: HRL, requestMethod: Option[String]): Option[(HRL, HRL)] = {
    findRewrite(hrl, requestMethod, forward)
  }

  def findRewriteBackward(hrl: HRL, requestMethod: Option[String]): Option[(HRL, HRL)] = {
    findRewrite(hrl, requestMethod, inverted)
  }

  private def findRewrite(hrl: HRL, requestMethod: Option[String], index: Map[IndexKey, HRL]): Option[(HRL, HRL)] = {
    val method = requestMethod.map(m ⇒ Method(m))
    findMostSpecificRewriteRule(index, method, hrl)
  }

  private def findMostSpecificRewriteRule(index: Map[IndexKey, HRL], method: Option[Method], originalHRL: HRL): Option[(HRL, HRL)] = {
    exactMatch(index, method, originalHRL) orElse patternMatch(index, method, originalHRL)
  }

  private def exactMatch(index: Map[IndexKey, HRL], method: Option[Method], originalHRL: HRL): Option[(HRL, HRL)] = {
    index
      .find(i ⇒ i._1 == IndexKey(originalHRL, method) || i._1 == IndexKey(originalHRL, None))
      .map(i ⇒ i._1.hrl → i._2)
  }

  private def patternMatch(index: Map[IndexKey, HRL], method: Option[Method], originalHRL: HRL): Option[(HRL, HRL)] = {
    index
      .find(i ⇒ ResourcePatternMatcher.matchResource(originalHRL, i._1.hrl).isDefined)
      .map(i ⇒ i._1.hrl → i._2)
  }
}

object RewriteIndex {
  def apply(): RewriteIndex = {
    RewriteIndex(Map.empty[IndexKey, HRL], Map.empty[IndexKey, HRL])
  }
}
