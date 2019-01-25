package services

import akka.NotUsed
import akka.stream.scaladsl.Source
import graphql.UserContext
import sangria.schema.Action

/**
  * A service to publish or subscribe to events
  *
  * @tparam T an entity which is published
  */
trait PubSubService[T] {

  /**
    * Publish an event
    */
  def publish(event: T)

  /**
    * Subscribe to the event by specified params.
    */
  def subscribe(eventNames: Seq[String])(implicit userContext: UserContext): Source[Action[Nothing, T], NotUsed]
}