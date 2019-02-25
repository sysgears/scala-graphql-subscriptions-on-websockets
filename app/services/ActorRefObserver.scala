package services

import akka.actor.ActorRef
import akka.actor.ActorRef.noSender
import monix.execution.Ack
import monix.execution.Ack.Continue
import monix.reactive.Observer
import play.api.Logger

import scala.concurrent.Future

case class ActorRefObserver[T](actorRef: ActorRef) extends Observer[T] {

  private val log = Logger(classOf[ActorRefObserver[T]])

  override def onNext(elem: T): Future[Ack] = {
    actorRef.tell(elem, noSender)
    Continue
  }

  override def onError(ex: Throwable): Unit = {
    log.info(s"Error has occurred. Reason: ${ex.getCause}")
  }

  override def onComplete(): Unit = {
    log.info(s"Event stream has closed.")
  }
}