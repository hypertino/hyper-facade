/*
 * Copyright (c) 2017 Magomed Abdurakhmanov, Hypertino
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.hypertino.facade.events

import java.nio.BufferOverflowException
import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.pattern.pipe
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.{ClientSpecificMethod, RequestContext}
import com.hypertino.facade.utils.{MetricUtils, TaskUtils}
import com.hypertino.facade.workers.RequestProcessor
import com.hypertino.hyperbus.{Hyperbus, model}
import com.hypertino.hyperbus.model._
import com.typesafe.scalalogging.StrictLogging
import monix.execution
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import monix.reactive.{Observer, OverflowStrategy}
import scaldi.Injector
import MetricUtils._
import scala.concurrent.Future

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperbus: Hyperbus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit val injector: Injector)
  extends Actor
    with RequestProcessor with StrictLogging {

  val maxSubscriptionTries = config.getInt(FacadeConfigPaths.MAX_SUBSCRIPTION_TRIES)
  val maxStashedEventsCount = config.getInt(FacadeConfigPaths.FEED_MAX_STASHED_EVENTS_COUNT)

  val scheduler: monix.execution.Scheduler = inject[monix.execution.Scheduler] // don't make this implicit

  def receive: Receive = stopStartSubscription orElse {
    case cwr: RequestContext ⇒
      implicit val ec = scheduler
      processRequestToFacade(cwr) runAsync ec pipeTo websocketWorker
  }

  def filtering(subscriptionSyncTries: Int): Receive = {
    case BeforeFilterComplete(cwr) ⇒
      continueSubscription(cwr, subscriptionSyncTries)
  }

  def subscribing(cwr: RequestContext, subscriptionSyncTries: Int, stashedEvents: Vector[StashedEvent]): Receive = {
    case event: DynamicRequest ⇒
      processEventWhileSubscribing(cwr, event, subscriptionSyncTries, stashedEvents)

    case resourceState: Response[DynamicBody]@unchecked ⇒
      processResourceState(cwr, resourceState, subscriptionSyncTries)

    case BecomeReliable(lastRevision: Long) ⇒
      val subscriber = reliableEventsObserver(cwr)
      if (stashedEvents.isEmpty) {
        context.become(subscribedReliable(cwr, lastRevision, subscriptionSyncTries, subscriber) orElse stopStartSubscription)
      } else {
        context.become(waitForUnstash(cwr, Some(lastRevision), subscriptionSyncTries, stashedEvents.tail, subscriber) orElse stopStartSubscription)
        logger.debug(s"Reliable subscription will be started for ${cwr.httpHeaders.hrl} with revision $lastRevision after unstashing of all events")
        unstash(stashedEvents.headOption)
      }


    case BecomeUnreliable ⇒
      if (stashedEvents.isEmpty) {
        context.become(subscribedUnreliable(cwr) orElse stopStartSubscription)
      } else {
        val subscriber = reliableEventsObserver(cwr)
        context.become(waitForUnstash(cwr, None, subscriptionSyncTries, stashedEvents.tail, subscriber) orElse stopStartSubscription)

        logger.debug(s"Unreliable subscription will be started for ${cwr.httpHeaders.hrl} after unstashing of all events")
        unstash(stashedEvents.headOption)
      }

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def waitForUnstash(cwr: RequestContext,
                     lastRevision: Option[Long],
                     subscriptionSyncTries: Int,
                     stashedEvents: Vector[StashedEvent],
                     subscriber: Observer[DynamicRequest]): Receive = {
    case event: DynamicRequest ⇒
      context.become(waitForUnstash(cwr, lastRevision, subscriptionSyncTries, stashedEvents :+ StashedEvent(event), subscriber))

    case StashedEvent(event) ⇒
      lastRevision match {
        case Some(revision) ⇒
          processReliableEvent(cwr, event, revision, subscriptionSyncTries, subscriber)
        case None ⇒
          processUnreliableEvent(cwr, event)
      }
      unstash(stashedEvents.headOption)

    case UnstashingCompleted ⇒
      logger.debug(s"Reliable subscription started for ${cwr.httpHeaders.hrl} with revision $lastRevision")
      if (stashedEvents.isEmpty) {
        lastRevision match {
          case Some(revision) ⇒
            context.become(subscribedReliable(cwr, revision, subscriptionSyncTries, subscriber) orElse stopStartSubscription)
          case None ⇒
            context.become(subscribedUnreliable(cwr) orElse stopStartSubscription)
        }
      } else {
        unstash(stashedEvents.headOption)
      }

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def subscribedReliable(cwr: RequestContext, lastRevisionId: Long, subscriptionSyncTries: Int, subscriber: Observer[DynamicRequest]): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(cwr, event, lastRevisionId, subscriptionSyncTries, subscriber)

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(cwr: RequestContext): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(cwr, event)
  }

  def stopStartSubscription: Receive = {
    case cwr: RequestContext if cwr.httpHeaders.method == ClientSpecificMethod.SUBSCRIBE ⇒
      startSubscription(cwr, 0)

    case cwr: RequestContext if cwr.httpHeaders.method == ClientSpecificMethod.UNSUBSCRIBE ⇒
      context.stop(self)
  }

  def startSubscription(cwr: RequestContext, subscriptionSyncTries: Int): Unit = {
    logger.trace(s"Starting subscription #$subscriptionSyncTries for ${cwr.httpHeaders.hrl}") // todo: shortcut to originalHeaders.hrl ?
    if (subscriptionSyncTries > maxSubscriptionTries) {
      logger.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxSubscriptionTries) for ${cwr.request}")
      context.stop(self)
    }
    subscriptionManager.off(self)

    context.become(filtering(subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = scheduler
    beforeResolvedFilterChain.filterRequest(cwr, metricsTracker) flatMap { unpreparedContextWithRequest ⇒
      prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest) map { cwrBeforeRaml ⇒
        BeforeFilterComplete(cwrBeforeRaml)
      }
    } onErrorRecover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } runAsync ec pipeTo self
  }

  def continueSubscription(cwr: RequestContext,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(cwr, subscriptionSyncTries, Vector.empty) orElse stopStartSubscription)

    implicit val ec = scheduler

    val trackRequestTime = metricsTracker.timer(MetricKeys.TOTAL_REQUEST_TIME).time()
    processRequestWithRaml(cwr) flatMap { cwrRaml ⇒
      metricsTracker.timeOfTask(MetricKeys.specificRequest(cwrRaml.request.headers.hrl.location)) {
        val correlationId = cwrRaml.request.correlationId
        val subscriptionUri = getSubscriptionUri(cwrRaml.request)
        subscriptionManager.off(self)
        subscriptionManager.subscribe(self, subscriptionUri, correlationId)
        implicit val mvx = MessagingContext(correlationId + self.path.toString) // todo: check what's here
        val message = cwrRaml.request.copy(
          headers = MessageHeaders
            .builder
            .++=(cwrRaml.request.headers)
            .withMethod(model.Method.GET)
            .requestHeaders()
        )
        hyperbus.ask(message)
      }
    } onErrorRecover {
      handleHyperbusExceptions(cwr)
    } runAsync ec andThen { case _ ⇒
      trackRequestTime.stop
    } pipeTo self
  }

  def processEventWhileSubscribing(cwr: RequestContext, event: DynamicRequest, subscriptionSyncTries: Int, stashedEvents: Vector[StashedEvent]): Unit = {
    logger.trace(s"Processing event while subscribing $event for ${cwr.httpHeaders.hrl}")

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(_) ⇒
        logger.trace(s"event $event is stashed because resource state is not fetched yet")
        context.become(subscribing(cwr, subscriptionSyncTries, stashedEvents :+ StashedEvent(event)))
        if (stashedEvents.length > maxStashedEventsCount) {
          self ! RestartSubscription
        }

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(cwr, event)
    }
  }

  def processResourceState(cwr: RequestContext, resourceState: DynamicResponse, subscriptionSyncTries: Int) = {
    logger.trace(s"Processing resource state $resourceState for ${cwr.httpHeaders.hrl}")

    implicit val ec = scheduler
    TaskUtils.chain(resourceState, cwr.stages.map { _ ⇒
      annotationsFilterChain.filterResponse(cwr, _: DynamicResponse, metricsTracker)
    }) flatMap { filteredResponse ⇒
      afterServiceReplyFilterChain.filterResponse(cwr, filteredResponse, metricsTracker) map { finalResponse ⇒
        websocketWorker ! finalResponse
        if (finalResponse.headers.statusCode > 399) { // failed
          PoisonPill
        }
        else {
          finalResponse.headers.get(Header.REVISION) match {
            // reliable feed
            case Some(revision) ⇒
              BecomeReliable(revision.toLong)

            // unreliable feed
            case _ ⇒
              BecomeUnreliable
          }
        }
      }
    } onErrorRecover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } runAsync ec pipeTo self
  }

  def processUnreliableEvent(cwr: RequestContext, event: DynamicRequest): Unit = {
    logger.trace(s"Processing unreliable event $event for ${cwr.httpHeaders.hrl}")
    implicit val ec = scheduler
    TaskUtils.chain(event, cwr.stages.map { _ ⇒
      annotationsFilterChain.filterEvent(cwr, _: DynamicRequest, metricsTracker)
    }) flatMap { e ⇒
      afterServiceReplyFilterChain.filterEvent(cwr, e, metricsTracker) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } onErrorRecover handleFilterExceptions(cwr) { response ⇒
      logger.trace(s"Event is discarded for ${cwr.httpHeaders.hrl} with filter response $response")
    } runAsync
  }

  def processReliableEvent(cwr: RequestContext,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int,
                           subscriber: Observer[DynamicRequest]): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision) ⇒
        val revisionId = revision.toLong
        logger.trace(s"Processing reliable event #$revisionId $event for ${cwr.httpHeaders.hrl}")

        if (revisionId == lastRevisionId + 1) {
          subscriber.onNext(event)
          context.become(subscribedReliable(cwr, lastRevisionId + 1, 0, subscriber) orElse stopStartSubscription)
        }
        else if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          logger.info(s"Subscription on ${cwr.httpHeaders.hrl} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
      // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        logger.error(s"Received event: $event without revisionId for reliable feed: ${cwr.httpHeaders.hrl}")
    }
  }

  //  todo: this method seems hacky
  //  in this case we allow regular expression in URL
  // todo: somethingssss!!!!
  def getSubscriptionUri(filteredRequest: DynamicRequest): HRL = {
    val hrl = filteredRequest.headers.hrl
    /*val newArgs: Map[String, TextMatcher] = UriParser.tokens(hrl.location).flatMap {
      case ParameterToken(name, PathMatchType) ⇒
        Some(name → RegexMatcher(hrl.query(name).toString + "/.*"))

      case ParameterToken(name, RegularMatchType) ⇒
        Some(name → hrl.query(name))

      case _ ⇒ None
    }.toMap*/
    //Uri(uri.pattern, newArgs)
    hrl
  }

  def unstash(event: Option[StashedEvent]): Unit = {
    event match {
      case Some(event) ⇒
        self ! event
      case None ⇒
        self ! UnstashingCompleted
    }
  }

  def reliableEventsObserver(cwr: RequestContext): Observer[DynamicRequest] = BufferedSubscriber.synchronous({
    new Subscriber[DynamicRequest] {
      override def scheduler: execution.Scheduler = FeedSubscriptionActor.this.scheduler

      val currentFilteringFuture = new AtomicReference[Option[Future[Unit]]](None) // todo: what the hell is this!!!????

      override def onNext(event: DynamicRequest): Future[Ack] = {
        implicit val ec = scheduler
        val filteringTask = TaskUtils.chain(event, cwr.stages.map { _ ⇒
          annotationsFilterChain.filterEvent(cwr, _: DynamicRequest, metricsTracker)
        }) flatMap { e ⇒
          afterServiceReplyFilterChain.filterEvent(cwr, e, metricsTracker)
        }
        if (currentFilteringFuture.get().isEmpty) {
          val newCurrentFilteringTask = filteringTask map { filteredRequest ⇒
            websocketWorker ! filteredRequest
          } onErrorRecover handleFilterExceptions(cwr) { response ⇒
            logger.trace(s"Event is discarded for ${cwr.httpHeaders.hrl} with filter response $response")
          }
          currentFilteringFuture.set(Some(newCurrentFilteringTask.runAsync))
        } else {
          val newCurrentFilteringFuture = currentFilteringFuture.get().get andThen {
            case _ ⇒
              filteringTask map { filteredRequest ⇒
                websocketWorker ! filteredRequest
              } onErrorRecover handleFilterExceptions(cwr) { response ⇒
                logger.trace(s"Event is discarded for ${cwr.httpHeaders.hrl} with filter response $response")
              }
          }
          currentFilteringFuture.set(Some(newCurrentFilteringFuture))
        }
        Continue
      }

      override def onError(ex: Throwable): Unit = {
        ex match {
          case _: BufferOverflowException ⇒
            logger.error(s"Backpressure overflow. Restarting...")
            self ! RestartSubscription

          case other ⇒
            logger.error(s"Error has occured on event processing. Restarting... $other")
            self ! RestartSubscription
        }
      }

      override def onComplete(): Unit = {}
    }
  }, OverflowStrategy.Fail(maxStashedEventsCount)) //todo: implement backpressure!
}

case class BecomeReliable(lastRevision: Long)

case object BecomeUnreliable

case object RestartSubscription

case class BeforeFilterComplete(cwr: RequestContext)

case class StashedEvent(event: DynamicRequest)

case object UnstashingCompleted

object FeedSubscriptionActor {
  def props(websocketWorker: ActorRef,
            hyperbus: Hyperbus,
            subscriptionManager: SubscriptionsManager)
           (implicit inj: Injector) = Props(new FeedSubscriptionActor(
    websocketWorker,
    hyperbus,
    subscriptionManager))
}
