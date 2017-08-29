package com.hypertino.facade.filter.raml

import com.hypertino.binders.value.{Obj, Text, Value}
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicBody, DynamicRequest, EmptyBody, HRL, Header, MessagingContext, Method, Ok}
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

      case "document" ⇒
        hyperbus.ask(DynamicRequest(hrl, Method.GET, EmptyBody)).map {
          case Ok(body: DynamicBody, _) ⇒
            Some(body.content)
        }
    }
  }
}
