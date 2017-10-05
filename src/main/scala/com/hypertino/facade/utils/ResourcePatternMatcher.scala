package com.hypertino.facade.utils


import java.net.URLDecoder

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.utils.uri._

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
      val sourceUriTokenIter = sourceTokens.iterator
      var matchesCorrectly = patternTokenIter.hasNext == sourceUriTokenIter.hasNext
      var previousSourceUriToken: Option[Token] = None
      val patternQuery = pattern.query.toMap
      val sourceQuery = source.query.toMap
      while (patternTokenIter.hasNext && sourceUriTokenIter.hasNext && matchesCorrectly) {
        val sourceUriToken = normalizePath(sourceUriTokenIter, previousSourceUriToken)
        val nextPatternToken = patternTokenIter.next()
        nextPatternToken match {
          case SlashToken ⇒
            matchesCorrectly = (SlashToken == sourceUriToken) &&
              (patternTokenIter.hasNext == sourceUriTokenIter.hasNext)

          case t: TextToken ⇒
            matchesCorrectly = (t == sourceUriToken) &&
              (patternTokenIter.hasNext == sourceUriTokenIter.hasNext)

          case ParameterToken(patternParamName) ⇒
            sourceUriToken match {
              case TextToken(value) ⇒
                val sourceParamValue = URLDecoder.decode(value, "UTF-8")
                args += patternParamName → sourceParamValue
                matchesCorrectly = patternQuery.get(patternParamName).map { paramPattern ⇒
                  matchResource(HRL(sourceParamValue.toString), HRL(paramPattern.toString)).isDefined &&
                    patternTokenIter.hasNext == sourceUriTokenIter.hasNext
                } getOrElse {
                  patternTokenIter.hasNext == sourceUriTokenIter.hasNext
                }

              case ParameterToken(paramName) ⇒
                matchesCorrectly = patternQuery.get(paramName).map { paramPattern ⇒
                  sourceQuery.get(paramName).map { sourceParamValue ⇒
                    matchResource(HRL(sourceParamValue.toString), HRL(paramPattern.toString)).isDefined
                  } getOrElse {
                    false
                  }
                } getOrElse {
                  patternTokenIter.hasNext == sourceUriTokenIter.hasNext
                }

              case _ ⇒
                matchesCorrectly = false
            }
        }
        previousSourceUriToken = Some(sourceUriToken)
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
}
