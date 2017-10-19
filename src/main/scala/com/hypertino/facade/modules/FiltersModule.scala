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

  bind[RamlFilterFactory] identifiedBy "deny" to injected[DenyFilterFactory]
  bind[RamlFilterFactory] identifiedBy "rewrite" to injected[RewriteFilterFactory]
  bind[RamlFilterFactory] identifiedBy "context_fetch" to injected[ContextFetchFilterFactory]
  bind[RamlFilterFactory] identifiedBy "extract_item" to injected[ExtractItemFilterFactory]
  bind[FieldFilterAdapterFactory] identifiedBy "field_filter_adapter" to injected[FieldFilterAdapterFactory]
  bind[RamlFilterFactory] identifiedBy "set" to injected[SetFilterFactory]
  bind[RamlFilterFactory] identifiedBy "forward" to injected[ForwardFilterFactory]

  bind[RamlFieldFilterFactory] identifiedBy "remove_field" to injected[RemoveFieldFilterFactory]
  bind[RamlFieldFilterFactory] identifiedBy "set_field" to injected[SetFieldFilterFactory]
  bind[RamlFieldFilterFactory] identifiedBy "fetch_field" to injected[FetchFieldFilterFactory]
  bind[RamlFieldFilterFactory] identifiedBy "deny_field" to injected[DenyFieldFilterFactory]

  bind[FilterChain] identifiedBy "before_filter_chain" to SimpleFilterChain(
    requestFilters = Seq(injected[HttpWsRequestFilter],
      injected[AuthorizationRequestFilter],
      injected[IdempotencyRequestFilter])
  )
  bind[FilterChain] identifiedBy "after_filter_chain" to SimpleFilterChain(
    responseFilters = Seq(
      injected[SelectFieldsResponseFilter],
      injected[IdempotencyResponseFilter],
      injected[HttpWsResponseFilter]
    ),
    eventFilters = Seq(injected[WsEventFilter])
  )
  bind[FilterChain] identifiedBy "raml_filter_chain" to injected[RamlFilterChain]
  bind[ExpressionEvaluator] identifiedBy "predicate_evaluator" to DefaultExpressionEvaluator
}
