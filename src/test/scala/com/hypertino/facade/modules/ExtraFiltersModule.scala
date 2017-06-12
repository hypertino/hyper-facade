package com.hypertino.facade.modules

import com.hypertino.auth.BasicAuthenticationService
import com.hypertino.facade.TestService
import com.hypertino.facade.filter.NoOpFilterFactory
import com.hypertino.facade.filter.model.RamlFilterFactory
import scaldi.Module

class ExtraFiltersModule extends Module {
  bind [RamlFilterFactory]           identifiedBy "paged"         to new NoOpFilterFactory
  bind [BasicAuthenticationService]  identifiedBy "authService"   to injected[BasicAuthenticationService]
  bind [TestService]                 identifiedBy "testService"   to injected[TestService]
}
