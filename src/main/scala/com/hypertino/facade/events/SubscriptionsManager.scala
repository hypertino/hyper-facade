package com.hypertino.facade.events

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import com.hypertino.facade.utils.ResourcePatternMatcher
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{DynamicRequest, DynamicRequestObservableMeta, HRL, Header, HeaderHRL, MessageHeaders}
import com.hypertino.hyperbus.transport.api.matchers.{RegexMatcher, RequestMatcher, Specific}
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Ack.Continue
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import scaldi.{Injectable, Injector}

import scala.collection.JavaConversions._
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class SubscriptionsManager(implicit inj: Injector) extends Injectable with StrictLogging {

  val hyperbus = inject[Hyperbus]
  implicit val actorSystem = inject[ActorSystem]
  implicit val scheduler = inject[Scheduler]
  val watchRef = actorSystem.actorOf(Props(new SubscriptionWatch(this)))

  def subscribe(clientActorRef: ActorRef, uri: HRL, correlationId: String): Unit =
    subscriptionManager.subscribe(clientActorRef, uri, correlationId)

  def off(clientActorRef: ActorRef) = subscriptionManager.off(clientActorRef)

  private val subscriptionManager = new Manager

  class Manager {
    val groupName = hyperbus.defaultGroupName
    val groupSubscriptions = scala.collection.mutable.Map[HRL, GroupSubscription]()
    val groupSubscriptionById = TrieMap[ActorRef, HRL]()

    case class ClientSubscriptionData(clientActorRef: ActorRef, hrl: HRL, correlationId: String)

    class GroupSubscription(groupUri: HRL, initialSubscription: ClientSubscriptionData) {
      val clientSubscriptions = new ConcurrentLinkedQueue[ClientSubscriptionData]()
      var hyperbusSubscription: Option[Cancelable] = None
      addClient(initialSubscription)

      val s = new Subscriber[DynamicRequest] {
        override implicit def scheduler: Scheduler = SubscriptionsManager.this.scheduler

        override def onNext(elem: DynamicRequest): Future[Ack] = {
          logger.trace(s"Event received ($groupName): ${elem}")
          for (consumer: ClientSubscriptionData ← clientSubscriptions) {
            try {
              // todo: query matching!
              val matched = ResourcePatternMatcher.matchResource(consumer.hrl, elem.headers.hrl).isDefined
              logger.trace(s"Event #(${elem.headers.messageId}) ${if (matched) "forwarded" else "NOT matched"} to ${consumer.clientActorRef}/${consumer.correlationId}")
              if (matched) {
                val request = elem.copy(
                  headers = MessageHeaders.builder
                    .++=(elem.headers)
                    .withCorrelation(consumer.correlationId)
                    .requestHeaders()
                )
                // todo: back pressure!
                consumer.clientActorRef ! request
              }
            }
            catch {
              case t: Throwable ⇒
                logger.error("Can't forward subscription event", t)
            }
          }
          Continue
        }

        override def onError(ex: Throwable): Unit = {
          logger.error(s"Error has occurred on event consumption from Hyperbus. $ex")
        }

        override def onComplete(): Unit = {

        }
      }

      val requestMatcher = RequestMatcher(Map(
        HeaderHRL.FULL_HRL → Seq(Specific(groupUri.location)),
        Header.METHOD → Seq(RegexMatcher("^feed:.*$"))
      ))
      hyperbusSubscription = Some(hyperbus.events(groupName, DynamicRequestObservableMeta(requestMatcher)).subscribe(s))


      def addClient(subscription: ClientSubscriptionData) = clientSubscriptions.add(subscription)

      def removeClient(clientActorRef: ActorRef): Boolean = {
        import scala.collection.JavaConversions._
        for (consumer: ClientSubscriptionData ← clientSubscriptions) {
          if (consumer.clientActorRef == clientActorRef)
            clientSubscriptions.remove(consumer)
        }
        clientSubscriptions.isEmpty
      }

      def off() = {
        hyperbusSubscription match {
          case Some(subscription) ⇒ subscription.cancel()
          case None ⇒ logger.warn("You cannot unsubscribe because you are not subscribed yet!")
        }
      }
    }

    def subscribe(clientActorRef: ActorRef, uri: HRL, correlationId: String): Unit = {
      watchRef ! NewSubscriber(clientActorRef)
      val subscriptionData = ClientSubscriptionData(clientActorRef, uri, correlationId)
      val groupUri = uri
      groupSubscriptionById += clientActorRef → groupUri
      groupSubscriptions.synchronized {
        groupSubscriptions.get(groupUri).map { list ⇒
          list.addClient(subscriptionData)
        } getOrElse {
          val groupSubscription = new GroupSubscription(groupUri, subscriptionData)
          groupSubscriptions += groupUri → groupSubscription
        }
      }
    }

    def off(clientActorRef: ActorRef) = {
      groupSubscriptionById.get(clientActorRef).foreach { groupUri ⇒
        groupSubscriptionById -= clientActorRef
        groupSubscriptions.synchronized {
          groupSubscriptions.get(groupUri).foreach { groupSubscription ⇒
            if (groupSubscription.removeClient(clientActorRef)) {
              groupSubscription.off()
              groupSubscriptions -= groupUri
            }
          }
        }
      }
    }
  }

}

case class NewSubscriber(actorRef: ActorRef)

class SubscriptionWatch(subscriptionManager: SubscriptionsManager) extends Actor with StrictLogging {
  override def receive: Receive = {
    case NewSubscriber(actorRef) ⇒
      logger.debug(s"Watching new subscriber $actorRef")
      context.watch(actorRef)

    case Terminated(actorRef) ⇒
      logger.debug(s"Actor $actorRef is died. Terminating subscription")
      subscriptionManager.off(actorRef)
  }
}
