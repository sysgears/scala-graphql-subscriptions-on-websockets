package controllers

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import graphql.{GraphQL, GraphQLSubscriptions, UserContext}
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import sangria.ast.Document
import sangria.ast.OperationType.Subscription
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.playJson._
import sangria.parser.QueryParser
import sangria.streaming.akkaStreams.AkkaSource

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object WebSocketFlowActor {

  /**
    * Returns an instance of Props for WebSocketFlowActor actor.
    *
    * @param outActor             an actor on which will be sent messages from the current actor.
    *                             Messages received by 'outActor' will be sent on a client over WebSocket connection
    * @param graphQL              an object containing a graphql schema of the entire application
    * @param controllerComponents base controller components dependencies that most controllers rely on.
    * @param ec                   execute program logic asynchronously, typically but not necessarily on a thread pool
    * @param mat                  an instance of an implementation of Materializer SPI (Service Provider Interface)
    * @return an instance of Props for WebSocketFlowActor actor
    */
  def props(outActor: ActorRef, graphQL: GraphQL, controllerComponents: ControllerComponents)
           (implicit ec: ExecutionContext, mat: Materializer): Props = {
    Props(new WebSocketFlowActor(outActor, graphQL, GraphQLSubscriptions(), controllerComponents))
  }
}

/**
  * An actor which will receive any messages sent by a client over WebSocket connection.
  *
  * @param outActor             an actor on which will be sent messages from the current actor.
  *                             Messages received by 'outActor' will be sent on a client over WebSocket connection
  * @param graphQL              an object containing a graphql schema of the entire application
  * @param graphQLSubscriptions an instance which contains graphql subscriptions which can be canceled on demand
  * @param controllerComponents base controller components dependencies that most controllers rely on
  * @param ec                   execute program logic asynchronously, typically but not necessarily on a thread pool
  * @param mat                  an instance of an implementation of Materializer SPI (Service Provider Interface)
  */
class WebSocketFlowActor(outActor: ActorRef,
                         graphQL: GraphQL,
                         graphQLSubscriptions: GraphQLSubscriptions,
                         controllerComponents: ControllerComponents)
                        (implicit ec: ExecutionContext,
                         mat: Materializer)
  extends GraphQlHandler(controllerComponents) with Actor {

  /** @inheritdoc*/
  override def postStop(): Unit = {
    graphQLSubscriptions.cancelAll()
  }

  /** @inheritdoc*/
  override def receive: Receive = {
    case message: String =>

      val maybeQuery = Try(Json.parse(message)) match {
        case Success(json) => parseToGraphQLQuery(json)
        case Failure(error) => throw new Error(s"Fail to parse a request body. Reason [$error]")
      }

      val source: AkkaSource[JsValue] = maybeQuery match {
        case Success((query, operationName, variables)) => executeQuery(query, graphQL, variables, operationName)
        case Failure(error) => Source.single(JsString(error.getMessage))
      }
      source.map(_.toString).runWith(Sink.actorRef[String](outActor, PoisonPill))
  }

  /**
    * Analyzes and executes an incoming GraphQL subscription, and returns a stream of elements.
    *
    * @param query     graphql body of request
    * @param graphQL   an object containing a graphql schema of the entire application
    * @param variables an incoming variables passed in the request
    * @param operation name of the operation (handle only subscriptions)
    * @param mat       an instance of an implementation of Materializer SPI (Service Provider Interface)
    * @return an instance of AkkaSource which represents a stream of elements
    */
  def executeQuery(query: String,
                   graphQL: GraphQL,
                   variables: Option[JsObject] = None,
                   operation: Option[String] = None)
                  (implicit mat: Materializer): AkkaSource[JsValue] = QueryParser.parse(query) match {

    case Success(queryAst: Document) =>
      queryAst.operationType(operation) match {
        case Some(Subscription) =>
          import sangria.execution.ExecutionScheme.Stream
          import sangria.streaming.akkaStreams._

          Executor.execute(
            schema = graphQL.Schema,
            queryAst = queryAst,
            variables = variables.getOrElse(Json.obj()),
            userContext = UserContext(Some(graphQLSubscriptions))
          ).recover {
            case error: QueryAnalysisError => Json.obj("BadRequest" -> error.resolveError)
            case error: ErrorWithResolver => Json.obj("InternalServerError" -> error.resolveError)
          }

        case _ => Source.single {
          Json.obj("UnsupportedType" -> JsString(s"$operation"))
        }
      }

    case Failure(ex) => Source.single {
      Json.obj("BadRequest" -> JsString(s"${ex.getMessage}"))
    }
  }
}