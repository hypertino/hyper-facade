package com.hypertino.facade.modules

import com.hypertino.facade.FacadeService
import com.hypertino.facade.events.SubscriptionsManager
import com.hypertino.facade.filter.parser.{DefaultExpressionEvaluator, ExpressionEvaluator}
import com.hypertino.facade.workers.HttpWorker
import scaldi.Module

class FacadeServiceModule extends Module {
  bind[HttpWorker] identifiedBy 'httpWorker to injected[HttpWorker]
  bind[SubscriptionsManager] identifiedBy 'subscriptionsManager to injected[SubscriptionsManager]
  bind[ExpressionEvaluator] identifiedBy "predicateEvaluator" to DefaultExpressionEvaluator
  bind[FacadeService] identifiedBy 'facadeService to injected[FacadeService]
}
