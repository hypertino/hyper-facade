/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.utils

import java.io.{StringReader, StringWriter}

import com.hypertino.binders.value.{Null, Obj, Text}
import com.hypertino.facade.model.FacadeHeaders
import com.hypertino.hyperbus.model.headers.PlainHeadersConverter
import com.hypertino.hyperbus.model.{BadRequest, DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, EmptyBody, ErrorBody, HRL, Header, MessageHeaders, MessagingContext, NoContent}
import com.hypertino.hyperbus.serialization.{JsonContentTypeConverter, MessageReader}
import com.hypertino.hyperbus.util.SeqGenerator
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.{HttpEntity, HttpRequest, HttpResponse, StatusCode, _}
import spray.httpx.unmarshalling.FormDataUnmarshallers

object MessageTransformer {
  def frameToRequest(frame: Frame, remoteAddress: String, httpRequest: HttpRequest): DynamicRequest = {
    val dynamicRequest = MessageReader.fromString(frame.payload.utf8String, DynamicRequest.apply)

    val uri = spray.http.Uri(dynamicRequest.headers.hrl.location)
    if (uri.scheme.nonEmpty || uri.authority.nonEmpty) {
      throw new IllegalArgumentException(s"Uri $uri has invalid format. Only path and query is allowed.")
    }

    // if location contains query, then parse it plus query if specified
    val hrl = HRL.fromURL(dynamicRequest.headers.hrl.location)
    val hrlWithQuery = hrl.copy(
      query = dynamicRequest.headers.hrl.query + hrl.query
    )

    dynamicRequest.copy(
      headers = MessageHeaders
        .builder
        .++=(dynamicRequest.headers)
        .++=(httpRequest.headers.map(kv ⇒ kv.name → Text(kv.value)))
        .+=(FacadeHeaders.REMOTE_ADDRESS → remoteAddress)
        .withHRL(hrlWithQuery)
        .requestHeaders()
    )
  }

  def httpToRequest(request: HttpRequest, remoteAddress: String): DynamicRequest = {
    val hrl = HRL.fromURL(request.uri.toString)
    val body = if (request.entity.isEmpty)
      EmptyBody
    else {
      requestBody(request)
    }

    val headers = MessageHeaders
      .builder
      .++=(request.headers.map(kv ⇒ kv.name → Text(kv.value)))
      .+=(FacadeHeaders.REMOTE_ADDRESS → remoteAddress)
      .withHRL(hrl)
      .withMethod(request.method.value.toLowerCase)
      .requestHeaders()

    val headersWithContext = headers
      .get(Header.CORRELATION_ID)
      .orElse(headers.get(Header.MESSAGE_ID))
      .map { _ ⇒
        headers
      }
      .getOrElse {
        val messageId = SeqGenerator.create()
        MessageHeaders
          .builder
          .++=(headers)
          .withMessageId(messageId)
          .requestHeaders()
      }

    DynamicRequest(body, headersWithContext)
  }

  def messageToFrame(message: DynamicMessage): Frame = {
    TextFrame(message.serializeToString)
  }

  def messageToHttpResponse(response: DynamicResponse): HttpResponse = {
    val responseData = response match {
      case NoContent(_) ⇒ HttpData.Empty
      case _ ⇒
        val bodyWriter = new StringWriter()
        try {
          response.body.serialize(bodyWriter)
          HttpData(bodyWriter.toString)
        }
        finally {
          bodyWriter.close()
        }
    }


    val headers = PlainHeadersConverter.toHttp(response.headers.underlying)
    HttpResponse(StatusCode.int2StatusCode(response.headers.statusCode),
      HttpEntity(contentTypeToSpray(response.headers.contentType), responseData), headers.map { case (name, value) ⇒
        RawHeader(name, value)
      }.toList
    )
  }

  private def requestBody(request: HttpRequest): DynamicBody = {
    // todo: content type/encoding from headers? !!!!

    request.entity.toOption match {
      case Some(HttpEntity.NonEmpty(contentType, data)) =>
        if (contentType.mediaType.value == "application/x-www-form-urlencoded") {
          FormDataUnmarshallers.UrlEncodedFormDataUnmarshaller(request.entity) match {
            case Right(formData) ⇒
              DynamicBody(Obj.from(formData.fields.map(kv ⇒ kv._1 → Text(kv._2)):_*), None)
            case Left(ex) ⇒
              throw BadRequest(ErrorBody("malformed-urlencoded-request", Some(ex.toString)))(MessagingContext.empty)
          }
        }
        else {
          DynamicBody(new StringReader(data.asString), None)
        }

      case None =>
        DynamicBody(Null, None)
    }
  }

  private def contentTypeToSpray(contentType: Option[String]): spray.http.ContentType = {
    contentType match {
      case None ⇒
        spray.http.ContentType(`application/json`, `UTF-8`)

      case Some(localContentType) ⇒
        val dynamicContentType = JsonContentTypeConverter.localContentTypeToUniversalJson(localContentType).toString
        val indexOfSlash = dynamicContentType.indexOf('/')
        val (mainType, subType) = indexOfSlash match {
          case -1 ⇒
            (dynamicContentType, "")
          case index ⇒
            val mainType = dynamicContentType.substring(0, indexOfSlash)
            val subType = dynamicContentType.substring(indexOfSlash + 1)
            (mainType, subType)
        }
        // todo: why we need to register??? replace with header?
        val mediaType = MediaTypes.register(MediaType.custom(mainType, subType, compressible = true, binary = false))
        spray.http.ContentType(mediaType, `UTF-8`)
    }
  }
}
