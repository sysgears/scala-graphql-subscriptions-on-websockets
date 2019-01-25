package services

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import graphql.UserContext
import models.Event
import monix.execution.Scheduler
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import sangria.schema.Action
import play.api.Logger

/**
  * An implementation of PubSubService.
  *
  * @param scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  *                  schedule the execution of units of work to run with a delay or periodically
  * @tparam T an entity which is published
  */
class PubSubServiceImpl[T <: Event[_]](implicit val scheduler: Scheduler) extends PubSubService[T] {
  lazy val source: PublishSubject[T] = PublishSubject[T]

  /** @inheritdoc */
  override def publish(event: T): Unit = {
    source.onNext(event).map {
      _ => Logger.debug(s"Event published [ $event ]")
    }
  }

  /** @inheritdoc */
  override def subscribe(eventNames: Seq[String])
                        (implicit userContext: UserContext): Source[Action[Nothing, T], NotUsed] = {
    require(eventNames.nonEmpty)
    Source
      .actorRef[T](16, OverflowStrategy.dropHead)
      .mapMaterializedValue {
        actorRef =>
          userContext.maybeGraphQlSubs.foreach {
            graphqlSubs =>
              graphqlSubs.add(source.subscribe(new ActorRefObserver[T](actorRef)))
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