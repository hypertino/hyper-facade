package com.hypertino.facade.utils

import java.net.MalformedURLException

import com.hypertino.facade.raml.{RamlAnnotation, RamlConfiguration, RewriteIndexHolder}
import com.hypertino.hyperbus.model.HRL
import spray.http.Uri.Path


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
          case Some(hrl) ⇒
            rewrittenHRL = rewrite(rewrittenHRL, hrl)
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
      var rewrittenUri = from
      RewriteIndexHolder.rewriteIndex.findRewriteBackward(from, Some(method)) match {
        case Some(uri) ⇒
          rewrittenUri = rewrite(rewrittenUri, uri)
        case None ⇒
      }
      rewrittenUri
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
          case Some(uri) ⇒
            rewrittenUri = rewrite(rewrittenUri, uri)
          case None ⇒
            rewritesLeft = 0
        }
      }
      rewrittenUri
    }
  }

  /*def addRootPathPrefix(rootPathPrefix: String)(hrl: HRL): HRL = {
    if (spray.http.Uri(hrl.location).scheme != "hb") { // hb:// scheme
      hrl
    }
    else {
      HRL(rootPathPrefix + hrl.location, hrl.query)
    }
  }

  def removeRootPathPrefix(rootPathPrefix: String, hrl: HRL): HRL = {
    val normalizedUri = spray.http.Uri(hrl.location)
    // todo: check scheme, server for http?
    if (normalizedUri.path.startsWith(Path(rootPathPrefix))) {
      val pathOffset = rootPathPrefix.length
      HRL(normalizedUri.path.toString.substring(pathOffset), hrl.query)
    } else {
      throw new MalformedURLException(s"$hrl doesn't starts with prefix $rootPathPrefix")
    }
  }*/

  def rewrite(from: HRL, to: HRL): HRL = {
    to
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
