/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.model

object ErrorCode {
  final val INTERNAL_SERVER_ERROR = "internal_server_error"
  final val NOT_FOUND = "not_found"
  final val USER_NOT_FOUND = "user_not_found"
  final val MULTIPLE_USERS_FOUND = "multiple_users_found"
  final val UNSUPPORTED_AUTHORIZATION_SCHEME = "unsupported_authorization_scheme"

  final val FIELD_IS_PROTECTED = "field_is_protected"
  final val CONDITION_CHECK_FAILURE = "condition_check_failure"
  final val RESOURCE_IS_NOT_COLLECTION = "resource_is_not_collection"
  final val COLLECTION_HAVE_MORE_THAN_1_ITEMS = "collection_have_more_than_1_items"
  final val COLLECTION_IS_EMPTY = "collection_is_empty"
  final val SINGLE_ITEM_NOT_FOUND = "single_item_not_found"
  final val SINGLE_ITEM_AMBIGUOUS = "single_item_ambiguous"

  final val REQUEST_IN_PROGRESS = "request_in_progress"
  final val MALFORMED_URLENCODED_REQUEST = "malformed_urlencoded_request"
  final val SERVICE_NOT_FOUND = "service_not_found"
  final val SERVICE_TIMEOUT = "service_timeout"
}
