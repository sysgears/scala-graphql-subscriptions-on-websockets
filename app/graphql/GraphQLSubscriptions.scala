package graphql

import monix.execution.Cancelable
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.concurrent.TrieMap
import scala.collection.mutable

case class GraphQLSubscriptions() {

  private[this] val subscriptions: ConcurrentLinkedQueue[Cancelable] = new ConcurrentLinkedQueue()

  /** Adds a new operationId/cancelable pair to subscriptions.
    * If the subscriptions already contains a
    * mapping for the operationId, it will be overridden by the new cancelable.
    */
  def add(cancelable: Cancelable): Unit = {
    this.subscriptions.add(cancelable)
  }

  def cancelAll(): Unit = {
    this.subscriptions.forEach(_.cancel())
  }

  def size: Int = {
    this.subscriptions.size
  }
}