package graphql.schemas

import akka.stream.Materializer
import com.google.inject.Inject
import graphql.UserContext
import graphql.resolvers.PostResolver
import models.{Post, PostEvent}
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.schema._
import sangria.streaming.akkaStreams._
import services.PubSubService

import scala.concurrent.ExecutionContext

/**
  * Contains the definitions of all query, mutations and subscriptions
  * that work with the entity 'Post'. Also it is a construction element
  * for the build graphql schema of the entire application.
  *
  * @param postResolver  an object containing all resolve functions to work with the entity of 'Post'
  * @param pubSubService an instance of an implementation of PubSubService which is used to publish events
  *                      or subscribe to some mutations
  * @param ec            execute program logic asynchronously, typically but not necessarily on a thread pool
  * @param mat           an instance of an implementation of Materializer SPI (Service Provider Interface)
  */
class PostSchema @Inject()(postResolver: PostResolver,
                           pubSubService: PubSubService[PostEvent])
                          (implicit ec: ExecutionContext, mat: Materializer) {

  /**
    * Convert a Post object to a Sangria graphql object.
    * Sangria macros deriveObjectType creates an ObjectType with fields found in the Post entity.
    */
  implicit val PostType: ObjectType[Unit, Post] = deriveObjectType[Unit, Post](ObjectTypeName("Post"))
  implicit val PostEventType: ObjectType[Unit, PostEvent] = deriveObjectType[Unit, PostEvent](ObjectTypeName("PostEvent"))

  /**
    * Enumeration with names for GraphQL fields of queries, mutations, and subscriptions
    */
  object FieldNames extends Enumeration {

    val posts: Value = Value("posts")
    val addPost: Value = Value("addPost")
    val findPost: Value = Value("findPost")
    val deletePost: Value = Value("deletePost")
    val editPost: Value = Value("editPost")
    val postsUpdated: Value = Value("postsUpdated")

    implicit def valueToString(value: Value): String = value.toString
  }

  import FieldNames._

  /**
    * List of queries to work with the entity of Post
    */
  val Queries: List[Field[UserContext, Unit]] = List(
    Field(
      name = posts,
      fieldType = ListType(PostType),
      resolve = _ => postResolver.posts
    ),
    Field(
      name = findPost,
      fieldType = OptionType(PostType),
      arguments = List(
        Argument("id", LongType)
      ),
      resolve = sangriaContext => postResolver.findPost(sangriaContext.args.arg[Long]("id"))
    )
  )

  /**
    * List of mutations to work with the entity of Post.
    */
  val Mutations: List[Field[UserContext, Unit]] = List(
    Field(
      name = addPost,
      fieldType = PostType,
      arguments = List(
        Argument("title", StringType),
        Argument("content", StringType)
      ),
      resolve = sangriaContext =>
        postResolver.addPost(
          sangriaContext.args.arg[String]("title"),
          sangriaContext.args.arg[String]("content")
        ).map {
          createdPost =>
            pubSubService.publish(PostEvent(addPost, createdPost))
            createdPost

        }
    ),
    Field(
      name = editPost,
      fieldType = PostType,
      arguments = List(
        Argument("id", LongType),
        Argument("title", StringType),
        Argument("content", StringType)
      ),
      resolve = sangriaContext =>
        postResolver.updatePost(
          Post(
            Some(sangriaContext.args.arg[Long]("id")),
            sangriaContext.args.arg[String]("title"),
            sangriaContext.args.arg[String]("content")
          )
        ).map {
          updatedPost =>
            pubSubService.publish(PostEvent(editPost, updatedPost))
            updatedPost
        }
    ),
    Field(
      name = deletePost,
      fieldType = PostType,
      arguments = List(
        Argument("id", LongType)
      ),
      resolve = sangriaContext => {
        val postId = sangriaContext.args.arg[Long]("id")
        postResolver.deletePost(postId)
          .map {
            deletedPost =>
              pubSubService.publish(PostEvent(deletePost, deletedPost))
              deletedPost
          }
      }
    )
  )

  /**
    * List of subscriptions to work with the entity of Post.
    */
  val Subscriptions: List[Field[UserContext, Unit]] = List(
    Field.subs(
      name = postsUpdated,
      fieldType = PostEventType,
      resolve = sangriaContext => {
        pubSubService.subscribe(Seq(addPost, deletePost, editPost))(sangriaContext.ctx)
      }
    )
  )
}
