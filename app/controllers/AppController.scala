package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import graphql.{GraphQL, UserContext}
import play.api.libs.EventSource
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import sangria.ast.Document
import sangria.ast.OperationType.{Mutation, Query, Subscription}
import sangria.execution._
import sangria.marshalling.playJson._
import sangria.parser.QueryParser

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Creates an `Action` to handle HTTP requests.
  *
  * @param graphQL              an object containing a graphql schema of the entire application
  * @param controllerComponents base controller components dependencies that most controllers rely on.
  * @param ec                   execute program logic asynchronously, typically but not necessarily on a thread pool
  * @param as                   an actor system is a hierarchical group of actors which share common
  *                             configuration, e.g. dispatchers, deployments, remote capabilities and
  *                             addresses. It is also the entry point for creating or looking up actors
  * @param mat                  an instance of an implementation of Materializer SPI (Service Provider Interface)
  */
@Singleton
class AppController @Inject()(graphQL: GraphQL,
                              controllerComponents: ControllerComponents)
                             (implicit val ec: ExecutionContext,
                              as: ActorSystem,
                              mat: Materializer) extends GraphQlHandler(controllerComponents) {

  /**
    * Renders an page with an in-browser IDE for exploring GraphQL.
    */
  def graphiql: Action[AnyContent] = Action(Ok(views.html.graphiql()))

  /**
    * Parses graphql body of incoming request.
    *
    * @return an 'Action' to handles a request and generates a result to be sent to the client
    */
  def graphqlBody: Action[JsValue] = Action.async(parse.json) {
    request: Request[JsValue] =>

      parseToGraphQLQuery(request.body) match {
        case Success((query, operationName, variables)) => executeQuery(query, variables, operationName)
        case Failure(error) => Future.successful(BadRequest(error.getMessage))
      }
  }

  /**
    * Accepts and processes GraphQL subscription over WebSocket connection
    *
    * @return an instance of WebSocket to handles a messages with GraphQL subscription
    *         and generates a result to be sent to the client
    */
  def graphqlSubscriptionOverWebSocket: WebSocket = WebSocket.accept[String, String] {
    _: RequestHeader =>
      ActorFlow.actorRef {
        actorRef =>
          WebSocketFlowActor.props(actorRef, graphQL, controllerComponents)
      }
  }

  /**
    * Accepts and processes GraphQL subscription over SSE connection
    *
    * @return an 'Action' to handles a request with GraphQL subscription and generates
    *         a result to be sent to the client
    */
  def graphqlSubscriptionOverSSE: Action[AnyContent] = Action.async {
    request =>

      val queryString: Option[String] = request.getQueryString("query")
      val variables: Option[JsObject] = request.getQueryString("variables").map(parseVariables)
      val operationType: Option[String] = request.getQueryString("operationName")

      queryString match {
        case Some(query) => executeQuery(query, variables, operationType)
        case _ => Future.successful(BadRequest("Request does not contain graphql query"))
      }
  }

  /**
    * Analyzes and executes incoming graphql query, and returns execution result.
    *
    * @param query     graphql body of request
    * @param variables incoming variables passed in the request
    * @param operation name of the operation (queries, mutations or subscriptions)
    * @return simple result, which defines the response header and a body ready to send to the client
    */
  def executeQuery(query: String,
                   variables: Option[JsObject] = None,
                   operation: Option[String] = None): Future[Result] = {

    QueryParser.parse(query) match {
      case Success(queryAst: Document) => {
        queryAst.operationType(operation) match {
          case Some(Subscription) =>
            import sangria.execution.ExecutionScheme.Stream
            import sangria.streaming.akkaStreams._

            val source: AkkaSource[JsValue] = Executor.execute(
              schema = graphQL.Schema,
              queryAst = queryAst,
              variables = variables.getOrElse(Json.obj()),
              userContext = UserContext()
            )
            Future(Ok.chunked(source via EventSource.flow).as(EVENT_STREAM))

          case Some(Query) | Some(Mutation) =>
            Executor
              .execute(
                schema = graphQL.Schema,
                queryAst = queryAst,
                variables = variables.getOrElse(Json.obj()),
                userContext = UserContext()
              )
              .map(Ok(_))
              .recover {
                case error: QueryAnalysisError => BadRequest(error.resolveError)
                case error: ErrorWithResolver => InternalServerError(error.resolveError)
              }
          case _ => Future(BadRequest("Unsupported graphql operation type"))
        }
      }

      case Failure(ex) => Future.successful(BadRequest(s"${ex.getMessage}"))
    }
  }
}
