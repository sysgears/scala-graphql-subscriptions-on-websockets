package services

import monix.reactive.Observable
import sangria.schema.Action

trait PubSubService[T] {

  /**
    * Publish an event
    */
  def publish(event: T)

  /**
    * Subscribe to the event by specified params.
    */
  def subscribe(eventNames: Seq[String]): Observable[Action[Nothing, T]]
}

case class Event[T](name: String, element: T)