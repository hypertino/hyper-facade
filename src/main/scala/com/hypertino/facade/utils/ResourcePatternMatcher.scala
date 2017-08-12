package com.hypertino.facade.utils


import java.net.URLDecoder

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.utils.uri._

import scala.annotation.tailrec
import scala.collection.mutable

// todo: this also needs total refactoring
object ResourcePatternMatcher {
  def matchResource(resource: HRL, resourcePatterns: Set[HRL]): Option[HRL] = {
    resourcePatterns
      .iterator
      .map(matchResource(resource, _))
      .find(_.nonEmpty)
      .flatten
  }

  def matchResource(source: HRL, pattern: HRL): Option[HRL] = {
    if (source.authority == pattern.authority) {
      val patternPathUri = pattern.path
      val sourceTokens = UriPathParser.tokens(source.path).toSeq
      var args = mutable.MutableList[(String, String)]()
      val patternTokens = UriPathParser.tokens(patternPathUri).toSeq
      val patternTokenIter = patternTokens.iterator
      val reqUriTokenIter = sourceTokens.iterator
      var matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
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

          case ParameterToken(patternParamName) ⇒
            resUriToken match {
              case TextToken(value) ⇒
                args += patternParamName → URLDecoder.decode(value, "UTF-8")
                matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
              case ParameterToken(_) ⇒
                matchesCorrectly = true
              case _ ⇒
                matchesCorrectly = false
            }
        }
        previousReqUriToken = Some(resUriToken)
      }
      if (!matchesCorrectly) None
      else
        Some(pattern.copy(query = Obj(args.map(kv ⇒ kv._1 → Text(kv._2)).toMap)))
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
