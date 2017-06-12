package com.hypertino.facade

/**
  * Created by maqdev on 6/12/17.
  */
trait MockContext {
  def mockContext(request: FacadeRequest) = FacadeRequestContext(
    "127.0.0.1", spray.http.Uri(request.uri.formatted), request.uri.formatted, request.method, request.headers, None, Map.empty
  ).prepare(request)
}
