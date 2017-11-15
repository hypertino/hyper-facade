/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.modules

import com.hypertino.facade.filter.chain.{FilterChain, RamlFilterChain, SimpleFilterChain}
import com.hypertino.facade.filter.model.{RamlFieldFilterFactory, RamlFilterFactory}
import com.hypertino.facade.filters.annotated._
import com.hypertino.facade.filters.chain.after_reply.{IdempotencyResponseFilter, SelectFieldsResponseFilter}
import com.hypertino.facade.filters.chain.after_resolved.ResourceResolvedRequestFilter
import com.hypertino.facade.filters.chain.before_resolved._
import com.hypertino.hyperbus.model.Status
import scaldi.Module

/*
chains/stages:

  1. before_resolved (raw http/ws request)
  2. after_resolved (resource in raml is resolved)
  3. annotations (annotation filters)
  4. TODO: before_service (before hyperbus)
  5. after_reply (after service is replied)

 */


class FiltersModule extends Module {
  Status.statusCodeToName.filter(_._1 >= 400).foreach { kv ⇒
    bind[RamlFilterFactory] identifiedBy kv._2 to injected[ErrorResponseFilterFactory]
  }

  bind[RamlFilterFactory] identifiedBy "rewrite" to injected[RewriteFilterFactory]
  bind[RamlFilterFactory] identifiedBy "context_fetch" to injected[ContextFetchFilterFactory]
  bind[RamlFilterFactory] identifiedBy "extract_item" to injected[ExtractItemFilterFactory]
  bind[FieldFilterAdapterFactory] identifiedBy "field_filter_adapter" to injected[FieldFilterAdapterFactory]
  bind[RamlFilterFactory] identifiedBy "set" to injected[SetFilterFactory]
  bind[RamlFilterFactory] identifiedBy "forward" to injected[ForwardFilterFactory]
  bind[RamlFilterFactory] identifiedBy "authorize" to injected[AuthorizeFilterFactory]

  // field filters
  bind[RamlFieldFilterFactory] identifiedBy "remove" to injected[RemoveFieldFilterFactory]
  bind[RamlFieldFilterFactory] identifiedBy "set" to injected[SetFieldFilterFactory]
  bind[RamlFieldFilterFactory] identifiedBy "fetch" to injected[FetchFieldFilterFactory]

  //bind[RamlFieldFilterFactory] identifiedBy "deny_field" to injected[ErrorResponseFieldFilterFactory]
  Status.statusCodeToName.filter(_._1 >= 400).foreach { kv ⇒
    bind[RamlFieldFilterFactory] identifiedBy kv._2 to injected[ErrorResponseFieldFilterFactory]
  }

  bind[FilterChain] identifiedBy "before_resolved" to SimpleFilterChain(
    requestFilters = Seq(injected[HttpWsRequestFilter],
      injected[AuthorizationRequestFilter],
      injected[IdempotencyRequestFilter])
  )
  bind[FilterChain] identifiedBy "after_resolved" to SimpleFilterChain(
    requestFilters = Seq(injected[ResourceResolvedRequestFilter])
  )
  bind[FilterChain] identifiedBy "after_reply" to SimpleFilterChain(
    responseFilters = Seq(
      injected[SelectFieldsResponseFilter],
      injected[IdempotencyResponseFilter],
      injected[HttpWsResponseFilter]
    ),
    eventFilters = Seq(injected[WsEventFilter])
  )
  bind[FilterChain] identifiedBy "annotations" to injected[RamlFilterChain]
}
