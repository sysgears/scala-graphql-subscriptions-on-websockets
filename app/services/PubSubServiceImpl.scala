package services

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import sangria.schema.Action
import utils.Logger

class PubSubServiceImpl[T <: Event[_]](implicit val scheduler: Scheduler)
  extends PubSubService[T]
    with Logger {
  lazy val source: PublishSubject[T] = PublishSubject[T]

  override def publish(event: T): Unit = {
    log.debug(s"Event published [ $event ]")
    source.onNext(event)
  }

  override def subscribe(eventNames: Seq[String]): Observable[Action[Nothing, T]] = {
    require(eventNames.nonEmpty)
    source.filter(event => eventNames.contains(event.name)).map {
      event =>
        log.debug(s"Sending event [ $event ] to client ...")
        Action(event)
    }
  }
}