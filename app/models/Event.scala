package models

/**
  * Use this trait for events which will be published.
  *
  */
trait Event {
  def name: String
}