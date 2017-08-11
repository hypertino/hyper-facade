package com.hypertino.facade.utils

import java.net.{MalformedURLException, URLDecoder}
import java.security.URIParameter

import com.hypertino.binders.value.{Null, Obj, Value}
import com.hypertino.facade.raml.{IndexKey, RamlAnnotation, RamlConfiguration, RewriteIndexHolder}
import com.hypertino.hyperbus.model.HRL
import com.hypertino.hyperbus.utils.uri.{ParameterToken, SlashToken, TextToken, UriPathParser}
import spray.http.Uri.Path

// todo: refactor this and make it injectable!!!
object HrlTransformer {
  def rewriteLinkToOriginal(from: HRL, maxRewrites: Int): HRL = {
    if (spray.http.Uri(from.location).scheme != "hb") // hb:// scheme
      from
    else {
      var rewritesLeft = maxRewrites
      var rewrittenHRL = from
      while (rewritesLeft > 0) {
        rewritesLeft -= 1
        RewriteIndexHolder.rewriteIndex.findRewriteBackward(rewrittenHRL, None) match {
          case Some((sourceHRL, destinationHRL)) ⇒
            rewrittenHRL = rewrite(rewrittenHRL, sourceHRL, destinationHRL)

          case None ⇒
            rewritesLeft = 0
        }
      }
      rewrittenHRL
    }
  }

  def rewriteBackward(from: HRL, method: String): HRL = {
    if (spray.http.Uri(from.location).scheme != "hb") // hb:// scheme
      from
    else {
      RewriteIndexHolder.rewriteIndex.findRewriteBackward(from, Some(method)) match {
        case Some((sourceHRL, destinationHRL)) ⇒
          rewrite(from, sourceHRL, destinationHRL)
        case None ⇒
          from
      }
    }
  }

  def rewriteLinkForward(from: HRL, maxRewrites: Int, ramlConfig: RamlConfiguration): HRL = {
    if (!linkIsRewriteable(from, ramlConfig))
      from
    else {
      var rewritesLeft = maxRewrites
      var rewrittenUri = from
      while (rewritesLeft > 0) {
        rewritesLeft -= 1
        RewriteIndexHolder.rewriteIndex.findRewriteForward(rewrittenUri, None) match {
          case Some((sourceHRL, destinationHRL)) ⇒
            rewrittenUri = rewrite(rewrittenUri, sourceHRL, destinationHRL)
          case None ⇒
            rewritesLeft = 0
        }
      }
      rewrittenUri
    }
  }

  def rewrite(hrl: HRL, sourcePattern: HRL, destinationPattern: HRL): HRL = {
    val flattenHRL = HRL.fromURL(hrl.toURL())
    ResourcePatternMatcher.matchResource(flattenHRL, sourcePattern.copy(query=Null)).map { matched ⇒
      val destPathTokens = UriPathParser.tokens(destinationPattern.path)
      val destPathParams = destPathTokens.collect {
        case ParameterToken(str, _) ⇒ str
      }
      val destPathParmsValues = destPathParams.map { param ⇒
        val v = {
          hrl.query match {
            case Obj(els) ⇒ els.get(param)
            case _ ⇒ None
          }
        }.orElse {
          val pattern = "{" + param + "}"
          sourcePattern.query match {
            case Obj(els) ⇒
              els
                .find(_._2.contains(pattern))
                .flatMap(kv ⇒ extractInnerParamValue(kv._2.toString, matched.query.toMap(kv._1).toString, param))
            case _ ⇒ None
          }
        }

        v.map(param → _)
      }
      if (destPathParmsValues.forall(_.isDefined)) {
        val destPathParmMap = destPathParmsValues.flatten.toMap

        val destQuery = Obj(destPathParmMap) + {
          flattenHRL.query match {
            case Obj(els) ⇒ Obj(els.filterNot(kv ⇒ destPathParmMap.contains(kv._1)))
            case other ⇒ other
          }
        }

        destinationPattern.copy(query=destQuery match {
          case Obj(v) if v.isEmpty ⇒ Null
          case other ⇒ other
        })
      }
      else {
        hrl
      }
    } getOrElse {
      hrl
    }
  }

  private def extractInnerParamValue(sourceParamPattern: String, sourceValue: String, innerParamName: String): Option[Value] = {
    ResourcePatternMatcher.matchResource(HRL(URLDecoder.decode(sourceValue, "UTF-8")), HRL(sourceParamPattern)).flatMap { matched ⇒
      matched.query match {
        case Obj(els) ⇒ els.get(innerParamName)
        case _ ⇒ None
      }
    }
  }

  private def linkIsRewriteable(from: HRL, ramlConfig: RamlConfiguration): Boolean = {
    val emptyScheme = spray.http.Uri(from.location).scheme.isEmpty
    val resourceConfigOpt = ramlConfig.resourcesByPattern.get(from.location) orElse
      ramlConfig.resourcesByPattern.get(from.location)
    val rewriteAllowed = resourceConfigOpt match {
      case Some(resourceConfig) ⇒
        resourceConfig.annotations.exists(_.name == RamlAnnotation.REWRITE)
      case None ⇒
        true
    }
    emptyScheme && rewriteAllowed
  }
}
