package com.hypertino.facade.model

import com.hypertino.facade.filter.http.AuthUser


object ContextStorage {
  val AUTH_USER = "authUser" // todo: rename!!!
  val IS_AUTHORIZED = "isAuthorized"

  implicit class ExtendFacadeRequestContext(contextWithRequest: ContextWithRequest) {

    def authUser: Option[AuthUser] = {
      contextWithRequest.contextStorage.get(AUTH_USER) match {
        case Some(user : AuthUser) ⇒ Some(user)
        case _ ⇒ None
      }
    }

    def isAuthorized: Boolean = {
      contextWithRequest.contextStorage.get(IS_AUTHORIZED) match {
        case Some(authorized: Boolean) ⇒ authorized
        case None ⇒ false
      }
    }
  }
}
