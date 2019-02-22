package models

/**
  * A class which used for publishing events related to Post.
  *
  * @param name a name of an event which will be published
  * @param post an instance of Post which will be published
  */
case class PostEvent(name: String, post: Post) extends Event
