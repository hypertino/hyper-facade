package com.hypertino.facade.modules

import com.hypertino.facade.filter.SelectFieldsResponseFilter
import com.hypertino.facade.filter.chain.after.IdempotencyResponseFilter
import com.hypertino.facade.filter.chain.before.{AuthorizationRequestFilter, IdempotencyRequestFilter}
import com.hypertino.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.http.{HttpWsRequestFilter, HttpWsResponseFilter, WsEventFilter}
import com.hypertino.facade.filter.model.{RamlFieldFilterFactory, RamlFilterFactory}
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator}
import com.hypertino.facade.filter.raml._
import scaldi.Module


class FiltersModule extends Module {

  bind [RamlFilterFactory]          identifiedBy "deny"                                 to injected[DenyFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "rewrite"                              to injected[RewriteFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "context_fetch"                        to injected[ContextFetchFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "extract_item"                         to injected[ExtractItemFilterFactory]
  bind [FieldFilterAdapterFactory]  identifiedBy "fieldFilterAdapter"                   to injected[FieldFilterAdapterFactory]
  bind [RamlFilterFactory]          identifiedBy "set"                                  to injected[SetFilterFactory]
  bind [RamlFilterFactory]          identifiedBy "forward"                              to injected[ForwardFilterFactory]

  bind [RamlFieldFilterFactory]     identifiedBy "removeField"                          to injected[RemoveFieldFilterFactory]
  bind [RamlFieldFilterFactory]     identifiedBy "setField"                             to injected[SetFieldFilterFactory]
  bind [RamlFieldFilterFactory]     identifiedBy "fetchField"                           to injected[FetchFieldFilterFactory]
  bind [RamlFieldFilterFactory]     identifiedBy "denyField"                            to injected[DenyFieldFilterFactory]

  bind [FilterChain]                identifiedBy "beforeFilterChain"                    to SimpleFilterChain(
    requestFilters            = Seq(injected[HttpWsRequestFilter],
                                    injected[AuthorizationRequestFilter],
                                    injected[IdempotencyRequestFilter])
  )
  bind [FilterChain]                identifiedBy "afterFilterChain"                     to SimpleFilterChain(
    responseFilters           = Seq(
      injected[SelectFieldsResponseFilter],
      injected[IdempotencyResponseFilter],
      injected[HttpWsResponseFilter]
    ),
    eventFilters              = Seq(injected[WsEventFilter])
  )
  bind [FilterChain]                identifiedBy "ramlFilterChain"                      to injected[RamlFilterChain]
  bind [ExpressionEvaluator]         identifiedBy "predicateEvaluator"                  to DefaultExpressionEvaluator
}
