package services

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import graphql.UserContext
import models.Event
import monix.execution.Scheduler
import monix.reactive.subjects.PublishSubject
import play.api.Logger
import sangria.schema.Action

/**
  * An implementation of PubSubService.
  *
  * @param scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  *                  schedule the execution of units of work to run with a delay or periodically
  * @tparam T an entity which is published
  */
class PubSubServiceImpl[T <: Event](implicit val scheduler: Scheduler) extends PubSubService[T] {
  private val subject: PublishSubject[T] = PublishSubject[T]
  private val bufferSize = 42

  /** @inheritdoc */
  override def publish(event: T): Unit = {
    subject.onNext(event).map {
      _ => Logger.debug(s"Event published [ $event ]")
    }
  }

  /** @inheritdoc */
  override def subscribe(eventNames: Seq[String])
                        (implicit userContext: UserContext): Source[Action[Nothing, T], NotUsed] = {
    require(eventNames.nonEmpty)
    Source
      .actorRef[T](bufferSize, OverflowStrategy.dropHead)
      .mapMaterializedValue {
        actorRef =>
          userContext.graphQlSubs.foreach {
            subs =>
              val cancelable = subject.subscribe(new ActorRefObserver[T](actorRef))
              subs.add(cancelable)
          }
          NotUsed
      }
      .filter {
        event =>
          eventNames.contains(event.name)
      }
      .map {
        event =>
          Logger.debug(s"Sending event [ $event ] to client ...")
          Action(event)
      }
  }
}