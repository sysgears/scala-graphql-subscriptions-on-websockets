package services

import akka.NotUsed
import akka.stream.scaladsl.Source
import sangria.schema.Action

trait PubSubService[T] {

  /**
    * Publish an event
    */
  def publish(event: T)

  /**
    * Subscribe to the event by specified params.
    */
  def subscribe(eventNames: Seq[String]): Source[Action[Nothing, T], NotUsed]
}

case class Event[T](name: String, element: T)