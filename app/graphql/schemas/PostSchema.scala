package graphql.schemas

import akka.stream.Materializer
import com.google.inject.Inject
import graphql.UserContext
import graphql.resolvers.PostResolver
import models.{Event, Post}
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
                           pubSubService: PubSubService[Event[Post]])
                          (implicit ec: ExecutionContext, mat: Materializer) {

  /**
    * Convert a Post object to a Sangria graphql object.
    * Sangria macros deriveObjectType creates an ObjectType with fields found in the Post entity.
    */
  implicit val PostType: ObjectType[Unit, Post] = deriveObjectType[Unit, Post](ObjectTypeName("Post"))

  /**
    * Object contains name aliases for GraphQL fields of queries, mutations and subscriptions
    */
  object Names {

    final val POSTS = "posts"
    final val ADD_POST = "addPost"
    final val FIND_POST = "findPost"
    final val DELETE_POST = "deletePost"
    final val EDIT_POST = "editPost"
    final val POSTS_UPDATED = "postsUpdated"
  }

  import Names._

  /**
    * List of queries to work with the entity of Post
    */
  val Queries: List[Field[UserContext, Unit]] = List(
    Field(
      name = POSTS,
      fieldType = ListType(PostType),
      resolve = _ => postResolver.posts
    ),
    Field(
      name = FIND_POST,
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
      name = ADD_POST,
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
            pubSubService.publish(new Event[Post](ADD_POST, createdPost))
            createdPost

        }
    ),
    Field(
      name = EDIT_POST,
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
            pubSubService.publish(new Event[Post](EDIT_POST, updatedPost))
            updatedPost
        }
    ),
    Field(
      name = DELETE_POST,
      fieldType = PostType,
      arguments = List(
        Argument("id", LongType)
      ),
      resolve = sangriaContext => {
        val postId = sangriaContext.args.arg[Long]("id")
        postResolver.deletePost(postId)
          .map {
            deletedPost =>
              pubSubService.publish(new Event[Post](DELETE_POST, deletedPost))
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
      name = POSTS_UPDATED,
      fieldType = PostType,
      resolve = sangriaContext => {
        pubSubService.subscribe(Seq(ADD_POST, DELETE_POST, EDIT_POST))(sangriaContext.ctx)
          .map(action => action.map(e => e.element))
      }
    )
  )
}
