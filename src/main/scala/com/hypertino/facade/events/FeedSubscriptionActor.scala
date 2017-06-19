package com.hypertino.facade.events

import java.nio.BufferOverflowException
import java.util.concurrent.atomic.AtomicReference

import akka.actor._
import akka.pattern.pipe
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.workers.RequestProcessor
import com.hypertino.facade.metrics.MetricKeys
import com.hypertino.facade.model.{ClientSpecificMethod, ContextWithRequest}
import com.hypertino.facade.raml.Method
import com.hypertino.facade.utils.FutureUtils
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.transport.api.matchers.{RegexMatcher, TextMatcher}
import monix.execution.Ack
import monix.reactive.{Observer, OverflowStrategy}
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import org.slf4j.LoggerFactory
import scaldi.Injector

import scala.concurrent.{ExecutionContext, Future}

class FeedSubscriptionActor(websocketWorker: ActorRef,
                            hyperbus: Hyperbus,
                            subscriptionManager: SubscriptionsManager)
                           (implicit val injector: Injector)
  extends Actor
    with RequestProcessor {

  val maxSubscriptionTries = config.getInt(FacadeConfigPaths.MAX_SUBSCRIPTION_TRIES)
  val maxStashedEventsCount = config.getInt(FacadeConfigPaths.FEED_MAX_STASHED_EVENTS_COUNT)

  val log = LoggerFactory.getLogger(getClass)
  val scheduler = inject[Scheduler] // don't make this implicit

  import context._

  def receive: Receive = stopStartSubscription orElse {
    case cwr: ContextWithRequest ⇒
      implicit val ec = scheduler
      processRequestToFacade(cwr) pipeTo websocketWorker
  }

  def filtering(subscriptionSyncTries: Int): Receive = {
    case BeforeFilterComplete(cwr) ⇒
      continueSubscription(cwr, subscriptionSyncTries)
  }

  def subscribing(cwr: ContextWithRequest, subscriptionSyncTries: Int, stashedEvents: Vector[StashedEvent]): Receive = {
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
        log.debug(s"Reliable subscription will be started for ${cwr.originalHeaders.hrl} with revision $lastRevision after unstashing of all events")
        unstash(stashedEvents.headOption)
      }


    case BecomeUnreliable ⇒
      if (stashedEvents.isEmpty) {
        context.become(subscribedUnreliable(cwr) orElse stopStartSubscription)
      } else {
        val subscriber = reliableEventsObserver(cwr)
        context.become(waitForUnstash(cwr, None, subscriptionSyncTries, stashedEvents.tail, subscriber) orElse stopStartSubscription)

        log.debug(s"Unreliable subscription will be started for ${cwr.originalHeaders.hrl} after unstashing of all events")
        unstash(stashedEvents.headOption)
      }

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def waitForUnstash(cwr: ContextWithRequest,
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
      log.debug(s"Reliable subscription started for ${cwr.originalHeaders.hrl} with revision $lastRevision")
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

  def subscribedReliable(cwr: ContextWithRequest, lastRevisionId: Long, subscriptionSyncTries: Int, subscriber: Observer[DynamicRequest]): Receive = {
    case event: DynamicRequest ⇒
      processReliableEvent(cwr, event, lastRevisionId, subscriptionSyncTries, subscriber)

    case RestartSubscription ⇒
      continueSubscription(cwr, subscriptionSyncTries + 1)
  }

  def subscribedUnreliable(cwr: ContextWithRequest): Receive = {
    case event: DynamicRequest ⇒
      processUnreliableEvent(cwr, event)
  }

  def stopStartSubscription: Receive = {
    case cwr: ContextWithRequest if cwr.originalHeaders.method == ClientSpecificMethod.SUBSCRIBE ⇒
      startSubscription(cwr, 0)

    case cwr: ContextWithRequest if cwr.originalHeaders.method == ClientSpecificMethod.UNSUBSCRIBE ⇒
      context.stop(self)
  }

  def startSubscription(cwr: ContextWithRequest, subscriptionSyncTries: Int): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Starting subscription #$subscriptionSyncTries for ${cwr.originalHeaders.hrl}") // todo: shortcut to originalHeaders.hrl ?
    }
    if (subscriptionSyncTries > maxSubscriptionTries) {
      log.error(s"Subscription sync attempts ($subscriptionSyncTries) has exceeded allowed limit ($maxSubscriptionTries) for ${cwr.request}")
      context.stop(self)
    }
    subscriptionManager.off(self)

    context.become(filtering(subscriptionSyncTries) orElse stopStartSubscription)

    implicit val ec = scheduler
    beforeFilterChain.filterRequest(cwr) map { unpreparedContextWithRequest ⇒
      val cwrBeforeRaml = prepareContextAndRequestBeforeRaml(unpreparedContextWithRequest)
      BeforeFilterComplete(cwrBeforeRaml)
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def continueSubscription(cwr: ContextWithRequest,
                           subscriptionSyncTries: Int): Unit = {

    context.become(subscribing(cwr, subscriptionSyncTries, Vector.empty) orElse stopStartSubscription)

    implicit val ec = scheduler

    val trackRequestTime = metrics.timer(MetricKeys.REQUEST_PROCESS_TIME).time()
    processRequestWithRaml(cwr) flatMap { cwrRaml ⇒
      val correlationId = cwrRaml.request.correlationId.get
      val subscriptionUri = getSubscriptionUri(cwrRaml.request)
      subscriptionManager.off(self)
      subscriptionManager.subscribe(self, subscriptionUri, correlationId)
      implicit val mvx = MessagingContext(correlationId + self.path.toString) // todo: check what's here
      val message = cwrRaml.request.copy(
        headers = Headers
          .builder
            .++=(cwrRaml.request.headers)
          .withMethod(Method.GET)
          .requestHeaders()
      )

      hyperbus.ask(message).runAsync
    } recover {
      handleHyperbusExceptions(cwr)
    } andThen { case _ ⇒
      trackRequestTime.stop
    } pipeTo self
  }

  def processEventWhileSubscribing(cwr: ContextWithRequest, event: DynamicRequest, subscriptionSyncTries: Int, stashedEvents: Vector[StashedEvent]): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing event while subscribing $event for ${cwr.context.pathAndQuery}")
    }

    event.headers.get(Header.REVISION) match {
      // reliable feed
      case Some(_) ⇒
        log.debug(s"event $event is stashed because resource state is not fetched yet")
        context.become(subscribing(cwr, subscriptionSyncTries, stashedEvents :+ StashedEvent(event)))
        if (stashedEvents.length > maxStashedEventsCount) {
          self ! RestartSubscription
        }

      // unreliable feed
      case _ ⇒
        processUnreliableEvent(cwr, event)
    }
  }

  def processResourceState(cwr: ContextWithRequest, resourceState: Response[DynamicBody], subscriptionSyncTries: Int) = {
    val facadeResponse = FacadeResponse(resourceState)
    if (log.isTraceEnabled) {
      log.trace(s"Processing resource state $resourceState for ${cwr.context.pathAndQuery}")
    }

    implicit val ec = executionContext
    FutureUtils.chain(facadeResponse, cwr.stages.map { _ ⇒
      ramlFilterChain.filterResponse(cwr, _ : FacadeResponse)
    }) flatMap { filteredResponse ⇒
      afterFilterChain.filterResponse(cwr, filteredResponse) map { finalResponse ⇒
        websocketWorker ! finalResponse
        if (finalResponse.status > 399) { // failed
          PoisonPill
        }
        else {
          finalResponse.headers.get(FacadeHeaders.CLIENT_REVISION) match {
            // reliable feed
            case Some(revision :: _) ⇒
              BecomeReliable(revision.toLong)

            // unreliable feed
            case _ ⇒
              BecomeUnreliable
          }
        }
      }
    } recover handleFilterExceptions(cwr) { response ⇒
      websocketWorker ! response
      PoisonPill
    } pipeTo self
  }

  def processUnreliableEvent(cwr: ContextWithRequest, event: DynamicRequest): Unit = {
    if (log.isTraceEnabled) {
      log.trace(s"Processing unreliable event $event for ${cwr.context.pathAndQuery}")
    }
    implicit val ec = executionContext

    FutureUtils.chain(FacadeRequest(event), cwr.stages.map { _ ⇒
      ramlFilterChain.filterEvent(cwr, _ : FacadeRequest)
    }) flatMap { e ⇒
      afterFilterChain.filterEvent(cwr, e) map { filteredRequest ⇒
        websocketWorker ! filteredRequest
      }
    } recover handleFilterExceptions(cwr) { response ⇒
      if (log.isDebugEnabled) {
        log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
      }
    }
  }

  def processReliableEvent(cwr: ContextWithRequest,
                           event: DynamicRequest,
                           lastRevisionId: Long,
                           subscriptionSyncTries: Int,
                           subscriber: Observer[DynamicRequest]): Unit = {
    event.headers.get(Header.REVISION) match {
      case Some(revision :: _) ⇒
        val revisionId = revision.toLong
        if (log.isTraceEnabled) {
          log.trace(s"Processing reliable event #$revisionId $event for ${cwr.context.pathAndQuery}")
        }

        if (revisionId == lastRevisionId + 1) {
          subscriber.onNext(event)
          context.become(subscribedReliable(cwr, lastRevisionId + 1, 0, subscriber) orElse stopStartSubscription)
        }
        else
        if (revisionId > lastRevisionId + 1) {
          // we lost some events, start from the beginning
          self ! RestartSubscription
          log.info(s"Subscription on ${cwr.context.pathAndQuery} lost events from $lastRevisionId to $revisionId. Restarting...")
        }
        // if revisionId <= lastRevisionId -- just ignore this event

      case _ ⇒
        log.error(s"Received event: $event without revisionId for reliable feed: ${cwr.context}")
    }
  }

  //  todo: this method seems hacky
  //  in this case we allow regular expression in URL
  def getSubscriptionUri(filteredRequest: FacadeRequest): HRL = {
    val uri = filteredRequest.uri
    val newArgs: Map[String, TextMatcher] = UriParser.tokens(uri.pattern.specific).flatMap {
      case ParameterToken(name, PathMatchType) ⇒
        Some(name → RegexMatcher(uri.args(name).specific + "/.*"))

      case ParameterToken(name, RegularMatchType) ⇒
        Some(name → uri.args(name))

      case _ ⇒ None
    }.toMap
    Uri(uri.pattern, newArgs)
  }

  def unstash(event: Option[StashedEvent]): Unit = {
    event match {
      case Some(event) ⇒
        self ! event
      case None ⇒
        self ! UnstashingCompleted
    }
  }

  def reliableEventsObserver(cwr: ContextWithRequest): Observer[DynamicRequest] = BufferedSubscriber.synchronous({
    new Subscriber[DynamicRequest] {
      val currentFilteringFuture = new AtomicReference[Option[Future[Unit]]](None)

      override def onNext(event: DynamicRequest): Future[Ack] = {
        val filteringFuture = FutureUtils.chain(FacadeRequest(event), cwr.stages.map { _ ⇒
          ramlFilterChain.filterEvent(cwr, _: FacadeRequest)
        }) flatMap { e ⇒
          afterFilterChain.filterEvent(cwr, e)
        }
        if (currentFilteringFuture.get().isEmpty) {
          val newCurrentFilteringFuture = filteringFuture map { filteredRequest ⇒
            websocketWorker ! filteredRequest
          } recover handleFilterExceptions(cwr) { response ⇒
            if (log.isDebugEnabled) {
              log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
            }
          }
          currentFilteringFuture.set(Some(newCurrentFilteringFuture))
        } else {
          val newCurrentFilteringFuture = currentFilteringFuture.get().get andThen {
            case _ ⇒
              filteringFuture map { filteredRequest ⇒
                websocketWorker ! filteredRequest
              } recover handleFilterExceptions(cwr) { response ⇒
                if (log.isDebugEnabled) {
                  log.debug(s"Event is discarded for ${cwr.context} with filter response $response")
                }
              }
          }
          currentFilteringFuture.set(Some(newCurrentFilteringFuture))
        }
      }

      override def onError(ex: Throwable): Unit = {
        ex match {
          case _ : BufferOverflowException ⇒
            log.error(s"Backpressure overflow. Restarting...")
            self ! RestartSubscription

          case other ⇒
            log.error(s"Error has occured on event processing. Restarting... $other")
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
case class BeforeFilterComplete(cwr: ContextWithRequest)
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
