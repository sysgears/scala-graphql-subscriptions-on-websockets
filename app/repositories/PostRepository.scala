package repositories

import models.Post

import scala.concurrent.Future

/**
  * Simple CRUD repository which provides basic operations.
  */
trait PostRepository {

  /**
    * Save an instance of Post to db.
    *
    * @param post a new instance
    * @return saved instance
    */
  def create(post: Post): Future[Post]

  /**
    * Returns instance by id.
    *
    * @param id an id of the instance
    * @return found instance
    */
  def find(id: Long): Future[Option[Post]]

  /**
    * Returns a list of instances.
    *
    * @return list of instance
    */
  def findAll(): Future[List[Post]]

  /**
    * Updates an existing instance.
    *
    * @param post new instance
    * @return updated instance
    */
  def update(post: Post): Future[Post]

  /**
    * Delete an existing instance by id.
    *
    * @param id an id of some instance
    * @return an instance of Post which was deleted
    */
  def delete(id: Long): Future[Option[Post]]
}