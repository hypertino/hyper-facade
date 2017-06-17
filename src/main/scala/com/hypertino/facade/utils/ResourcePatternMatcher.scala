package com.hypertino.facade.utils


import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.raml.utils._

import scala.annotation.tailrec
import scala.collection.mutable

object ResourcePatternMatcher {

  /**
    * Matches uri against sequence of uri patterns
    * @param resource - uri
    * @param resourcePatterns - sequence uri patterns
    * @return
    */
  def matchResource(resource: String, resourcePatterns: Seq[String]): Option[HRL] = {
    resourcePatterns
      .iterator
      .map(matchResource(resource, _))
      .find(_.nonEmpty)
      .flatten
  }

  /**
    * Matches URI pattern with request URI
    * @param resource - request resource
    * @param pattern - URI pattern from RAML configuration
    * @return if request URI matches pattern then Some of constructed URI with parameters will be returned, None otherwise
    */
  def matchResource(resource: String, pattern: String): Option[HRL] = {
    val resourceTokens = UriParser.tokens(resource)
    var args = mutable.MutableList[(String, String)]()
    val patternTokens = UriParser.tokens(pattern)
    val patternTokenIter = patternTokens.iterator
    val reqUriTokenIter = resourceTokens.iterator
    var matchesCorrectly = patternTokenIter.hasNext && reqUriTokenIter.hasNext
    var previousReqUriToken: Option[Token] = None
    while(patternTokenIter.hasNext && reqUriTokenIter.hasNext && matchesCorrectly) {
      val reqUriToken = getRequestUriToken(reqUriTokenIter, previousReqUriToken)
      patternTokenIter.next() match {
        case patternToken @ (TextToken(_) | SlashToken) ⇒
          matchesCorrectly = (patternToken == reqUriToken) &&
                 (patternTokenIter.hasNext == reqUriTokenIter.hasNext)

        case ParameterToken(patternParamName, RegularMatchType) ⇒
          reqUriToken match {
            case TextToken(value) ⇒
              args += patternParamName → value
              matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
            case _ ⇒
              matchesCorrectly = false
          }

        case ParameterToken(paramName, PathMatchType) ⇒
          reqUriToken match {
            case TextToken(value) ⇒
              args += paramName → foldUriTail(value, reqUriTokenIter)
            case ParameterToken(_, PathMatchType) ⇒
              matchesCorrectly = true
            case _ ⇒
              matchesCorrectly = false
          }
      }
      previousReqUriToken = Some(reqUriToken)
    }
    if (!matchesCorrectly) None
    else Some(HRL(pattern, Obj.from(args.map(kv ⇒ kv._1 → Text(kv._2)): _*)))
  }

  /**
    * This method removes multiple slashes in request
    */
  def getRequestUriToken(iter: Iterator[Token], previousToken: Option[Token]): Token = {
    iter.next() match {
      case SlashToken ⇒ previousToken match {
        case Some(SlashToken) ⇒
          if (iter.hasNext) getRequestUriToken(iter, previousToken)
          else SlashToken
        case Some(_) ⇒ SlashToken
        case None ⇒ SlashToken
      }

      case other ⇒ other
    }
  }

  /**
    * It's like toString for iterator of URI tokens
    * @param uriTail - string of merged URI tokens
    * @param iter - remaining tokens
    * @return
    */
  @tailrec private def foldUriTail(uriTail: String, iter: Iterator[Token]): String = {
    if (iter.hasNext) {
      val tokenStr = iter.next() match {
        case TextToken(value) ⇒ value
        case SlashToken ⇒ "/"
      }
      foldUriTail(uriTail + tokenStr, iter)
    } else uriTail
  }
}
