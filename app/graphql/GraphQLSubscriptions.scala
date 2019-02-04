package graphql

import monix.execution.Cancelable
import play.api.Logger

import scala.collection.mutable.ArrayBuffer

/**
  * A class which contains a list of subscriptions which was opened
  * during one WebSocket connection by a user and which can be canceled on demand.
  *
  */
case class GraphQLSubscriptions() {

  private[this] val subscriptions: ArrayBuffer[Cancelable] = ArrayBuffer.empty[Cancelable]
  private var closed = false

  /**
    * Adds a new cancelable to subscriptions.
    */
  def add(cancelable: Cancelable): Unit = this.synchronized {
    if (!closed) {
      cancelable +: subscriptions
    } else {
      Logger.debug("WebSocket connection was already closed!")
    }
  }

  /**
    * Cancels all subscriptions opened during one WebSocket connection
    * and clears the subscriptions queue.
    */
  def cancelAll(): Unit = this.synchronized {
    subscriptions.foreach(_.cancel())
    subscriptions.clear()
    closed = true
  }

  /**
    * Returns a number of opened subscriptions during one WebSocket connection.
    *
    * @return a number of opened subscriptions
    */
  def subscriptionsCount: Int = {
    this.subscriptions.size
  }
}