package com.hypertino.facade.utils


import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.utils.uri._

import scala.annotation.tailrec
import scala.collection.mutable

object ResourcePatternMatcher {

  /**
    * Matches uri against sequence of uri patterns
    * @param resource - uri
    * @param resourcePatterns - sequence uri patterns
    * @return
    */
  def matchResource(resource: String, resourcePatterns: Set[String]): Option[HRL] = {
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
    val resourceUri = spray.http.Uri(resource)
    val prefix = if (resourceUri.scheme.nonEmpty && resourceUri.authority.nonEmpty)
      resourceUri.scheme + ":" + resourceUri.authority
    else
      "/"

    if (pattern.startsWith(prefix)) {
      val patternPathUri = pattern.substring(prefix.length)
      val resourceTokens = UriPathParser.tokens(resource.substring(prefix.length))
      var args = mutable.MutableList[(String, String)]()
      val patternTokens = UriPathParser.tokens(patternPathUri)
      val patternTokenIter = patternTokens.iterator
      val reqUriTokenIter = resourceTokens.iterator
      var matchesCorrectly = patternTokenIter.hasNext && reqUriTokenIter.hasNext
      var previousReqUriToken: Option[Token] = None
      while (patternTokenIter.hasNext && reqUriTokenIter.hasNext && matchesCorrectly) {
        val resUriToken = normalizePath(reqUriTokenIter, previousReqUriToken)
        val nextPatternToken = patternTokenIter.next()
        nextPatternToken match {
          case SlashToken ⇒
            matchesCorrectly = (SlashToken == resUriToken) &&
              (patternTokenIter.hasNext == reqUriTokenIter.hasNext)

          case t: TextToken ⇒
            matchesCorrectly = (t == resUriToken) &&
              (patternTokenIter.hasNext == reqUriTokenIter.hasNext)

          case ParameterToken(patternParamName, RegularMatchType) ⇒
            resUriToken match {
              case TextToken(value) ⇒
                args += patternParamName → value
                matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
              case _ ⇒
                matchesCorrectly = false
            }

          case ParameterToken(paramName, PathMatchType) ⇒
            resUriToken match {
              case TextToken(value) ⇒
                args += paramName → foldUriTail(value, reqUriTokenIter)
              case ParameterToken(_, PathMatchType) ⇒
                matchesCorrectly = true
              case _ ⇒
                matchesCorrectly = false
            }
        }
        previousReqUriToken = Some(resUriToken)
      }
      if (!matchesCorrectly) None
      else
        Some(HRL(pattern, Obj.from(args.map(kv ⇒ kv._1 → Text(kv._2)): _*)))
    }
    else {
      None
    }
  }

  /**
    * This method removes multiple slashes in request
    */
  def normalizePath(iter: Iterator[Token], previousToken: Option[Token]): Token = {
    iter.next() match {
      case SlashToken ⇒ previousToken match {
        case Some(SlashToken) ⇒
          if (iter.hasNext) normalizePath(iter, previousToken)
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
