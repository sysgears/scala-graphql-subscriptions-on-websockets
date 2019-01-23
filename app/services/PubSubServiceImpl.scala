package services

import akka.NotUsed
import akka.stream.scaladsl.Source
import models.Event
import monix.execution.Scheduler
import monix.reactive.subjects.PublishSubject
import sangria.schema.Action
import utils.Logger

/**
  * An implementation of PubSubService.
  *
  * @param scheduler is an `scala.concurrent.ExecutionContext` that additionally can
  *                  schedule the execution of units of work to run with a delay or periodically
  * @tparam T an entity which is published
  */
class PubSubServiceImpl[T <: Event[_]](implicit val scheduler: Scheduler) extends PubSubService[T]
  with Logger {
  lazy val source: PublishSubject[T] = PublishSubject[T]

  /** @inheritdoc */
  override def publish(event: T): Unit = {
    log.debug(s"Event published [ $event ]")
    source.onNext(event)
  }

  /** @inheritdoc */
  override def subscribe(eventNames: Seq[String]): Source[Action[Nothing, T], NotUsed] = {
    require(eventNames.nonEmpty)
    Source.fromPublisher(source.toReactivePublisher).filter(event => eventNames.contains(event.name)).map {
      event =>
        log.debug(s"Sending event [ $event ] to client ...")
        Action(event)
    }
  }
}