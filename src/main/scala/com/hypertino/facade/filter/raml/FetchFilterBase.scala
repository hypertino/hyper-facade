package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Lst, Obj, Text, Value}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, EmptyBody, ErrorBody, HRL, Header, InternalServerError, MessagingContext, Method, NotFound, Ok}
import monix.eval.Task

trait FetchFilterBase {
  protected def expects: String
  protected def hyperbus: Hyperbus
  protected def ask(hrl: HRL)(implicit mcx: MessagingContext): Task[Option[Value]] = {
    expects match {
      case "collection_link" ⇒
        val hrlCollectionLink = hrl.copy(query = hrl.query + Obj.from("per_page" → 0))
        hyperbus.ask(DynamicRequest(hrlCollectionLink, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            Some(Obj(Map(
              "first_page_url" → Text(hrl.toURL())
            ) ++
              response.headers.get(Header.COUNT).map("count" → _)
            ))
        }

      case "collection_top" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            Some(
              Obj(
                Map("top" → body.content) ++
                  response.headers.link.map(kv ⇒ kv._1 → Text(kv._2.toURL())) ++
                  response.headers.get(Header.COUNT).map("count" → _).toMap
              )
            )
        }

      case "single_item" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case response @ Ok(body: DynamicBody, _) ⇒
            body.content match {
              case Lst(l) if l.size == 1 ⇒ Some(l.head)
              case Lst(l) if l.isEmpty ⇒
                throw NotFound(ErrorBody("single-item-not-found", Some(s"$hrl")))
              case Lst(_) ⇒
                throw InternalServerError(ErrorBody("single-item-ambiguous", Some(s"$hrl")))
              case o: Obj ⇒
                throw InternalServerError(ErrorBody("resource-is-not-a-collection", Some(s"$hrl")))
            }
        }

      case "document" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case Ok(body: DynamicBody, _) ⇒
            Some(body.content)
        }
    }
  }
}
