package services

import models.Event

import scala.concurrent.{ExecutionContext, Future}

/**
  * Implicits helpers for [[PubSubService]] instances.
  *
  */
object RichPubSubService {

  implicit class Publisher[T](element: Future[T])(implicit executionContext: ExecutionContext) {

    /**
      * Publish the element with specifying a name of an event
      *
      * @param eventName     a name of an event which is published
      * @param pubSubService an instance of a service for publishing events
      * @return an instance which was published
      */
    def pub(eventName: String)(implicit pubSubService: PubSubService[Event[T]]): Future[T] = {
      element.map {
        publishingElement => {
          pubSubService.publish(Event(eventName, publishingElement))
          publishingElement
        }
      }
    }
  }

}