package models

/**
  * Use instances of this class in PubSubService.
  *
  * @param name a name of an event which will be published
  * @param element an instance which will be published
  * @tparam T a type of instance which will be published
  */
case class Event[T](name: String, element: T)