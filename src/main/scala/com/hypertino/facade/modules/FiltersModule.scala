package com.hypertino.facade.modules

import com.hypertino.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.http.{AuthenticationRequestFilter, HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import com.hypertino.facade.filter.model.RamlFilterFactory
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator}
import com.hypertino.facade.filter.raml._
import scaldi.Module


class FiltersModule extends Module {

  bind [RamlFilterFactory]          identifiedBy "deny"                                 to injected[DenyFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "private"                              to injected[PrivateFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "set"                                  to injected[SetFieldFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "rewrite"                              to injected[RewriteFilterFactory]

  bind [FilterChain]                identifiedBy "beforeFilterChain"                    to SimpleFilterChain(
    requestFilters            = Seq(injected[HttpWsRequestFilter],
                                    injected[AuthenticationRequestFilter])
  )
  bind [FilterChain]                identifiedBy "afterFilterChain"                     to SimpleFilterChain(
    responseFilters           = Seq(injected[HttpWsResponseFilter]),
    eventFilters              = Seq(injected[WsEventFilter])
  )
  bind [FilterChain]                identifiedBy "ramlFilterChain"                      to injected[RamlFilterChain]
  bind [ExpressionEvaluator]         identifiedBy "predicateEvaluator"                  to DefaultExpressionEvaluator
}
