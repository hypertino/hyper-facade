package com.hypertino.facade.filters.chain.after_resolved

import com.hypertino.binders.value.{Obj, Text}
import com.hypertino.facade.filter.model.RequestFilter
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model.RequestContext
import com.hypertino.facade.raml.{Method, RamlConfiguration, RamlResourceMethod}
import com.hypertino.facade.utils.RequestUtils
import com.hypertino.hyperbus.model.HRL
import com.typesafe.config.Config
import monix.eval.Task
import monix.execution.Scheduler

class ResourceResolvedRequestFilter (config: Config, ramlConfig: RamlConfiguration,
                                     protected val expressionEvaluator: ExpressionEvaluator) extends RequestFilter {

  override def apply(requestContext: RequestContext)(implicit scheduler: Scheduler) = Task.now {

    // todo: encapsulate resourcesByPattern
    ramlConfig.resourcesByPattern.get(requestContext.request.headers.hrl.location) match {
      case Some(resource) ⇒
        resource.methods.get(Method(requestContext.request.headers.method)) match {
          case Some(resourceMethod) ⇒
            addDefaultQueryParameters(requestContext, resourceMethod)

          case _ ⇒
            requestContext
        }

      case _ ⇒
        requestContext
    }
  }

  private def addDefaultQueryParameters(requestContext: RequestContext, resourceMethod: RamlResourceMethod): RequestContext = {
    if (resourceMethod.queryParameters.nonEmpty) {
      val hrl = requestContext.request.headers.hrl
      val q = hrl.query.toMap
      val defaultValues = resourceMethod.queryParameters.flatMap {
        case (k, f) if !q.contains(k) && f.defaultValue.isDefined ⇒ Some(k → Text(f.defaultValue.get))
        case _ ⇒ None
      }

      val newHrl = HRL(hrl.location, Obj(q ++ defaultValues))
      requestContext.copy(
        request = RequestUtils.copyWith(requestContext.request, newHrl)
      )
    }
    else {
      requestContext
    }
  }
}
