package controllers

import akka.stream.scaladsl.Source
import com.google.inject.{Inject, Singleton}
import graphql.GraphQL
import monix.execution.Scheduler
import play.api.libs.EventSource
import play.api.libs.json._
import play.api.mvc._
import sangria.ast.Document
import sangria.ast.OperationType.Subscription
import sangria.execution._
import sangria.marshalling.playJson._
import sangria.parser.QueryParser
import utils.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Creates an `Action` to handle HTTP requests.
  *
  * @param graphQL          an object containing a graphql schema of the entire application
  * @param cc               base controller components dependencies that most controllers rely on.
  * @param executionContext execute program logic asynchronously, typically but not necessarily on a thread pool
  */
@Singleton
class AppController @Inject()(graphQL: GraphQL, cc: ControllerComponents)
                             (implicit val executionContext: ExecutionContext,
                              scheduler: Scheduler) extends AbstractController(cc)
  with Logger {

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
    implicit request: Request[JsValue] =>
      val extract: JsValue => (String, Option[String], Option[JsObject]) = query =>
        (
          (query \ "query").as[String],
          (query \ "operationName").asOpt[String],
          (query \ "variables").toOption.flatMap {
            case JsString(vars) => Some(parseVariables(vars))
            case obj: JsObject => Some(obj)
            case _ => None
          }
        )

      val maybeQuery: Try[(String, Option[String], Option[JsObject])] = Try {
        request.body match {
          case arrayBody@JsArray(_) => extract(arrayBody.value(0))
          case objectBody@JsObject(_) => extract(objectBody)
          case otherType =>
            throw new Error {
              s"/graphql endpoint does not support request body of type [${otherType.getClass.getSimpleName}]"
            }
        }
      }

      maybeQuery match {
        case Success((query, operationName, variables)) => executeQuery(query, variables, operationName)
        case Failure(error) =>
          Future.successful {
            BadRequest(error.getMessage)
          }
      }
  }

  def graphqlSubscription: Action[AnyContent] = Action.async {
    request =>

      val queryString: Option[String] = request.getQueryString("query") //todo: check multiple subscriptions in one query

      val variables: Option[JsObject] = request.getQueryString("variables").map(parseVariables) //todo: check variables

      val operationType: Option[String] = queryString.map(_.split(" ")).map(_.head)

      queryString match {
        case Some(query) =>
          executeQuery(query, variables, operationType)
        case _ => Future.successful(BadRequest("Request does not contain graphql query"))
      }
  }

  /**
    * Analyzes and executes incoming graphql query, and returns execution result.
    *
    * @param query     graphql body of request
    * @param variables incoming variables passed in the request
    * @param operation name of the operation (queries or mutations)
    * @return simple result, which defines the response header and a body ready to send to the client
    */
  def executeQuery(query: String,
                   variables: Option[JsObject] = None,
                   operation: Option[String] = None): Future[Result] = QueryParser.parse(query) match {
    case Success(queryAst: Document) => {
      queryAst.operationType(operation) match {
        case Some(Subscription) =>
          import sangria.execution.ExecutionScheme.Stream
          import sangria.streaming.monix._

          val res = Executor.execute(
            schema = graphQL.Schema,
            queryAst = queryAst,
            variables = variables.getOrElse(Json.obj()),
          )
          val src = Source.fromPublisher(res.toReactivePublisher)

          Future(Ok.chunked(src via EventSource.flow).as(EVENT_STREAM))
        case _ =>
          Executor
            .execute(
              schema = graphQL.Schema,
              queryAst = queryAst,
              variables = variables.getOrElse(Json.obj()),
            )
            .map(Ok(_))
            .recover {
              case error: QueryAnalysisError => BadRequest(error.resolveError)
              case error: ErrorWithResolver => InternalServerError(error.resolveError)
            }
      }
    }

    case Failure(ex) => Future.successful(BadRequest(s"${ex.getMessage}"))
  }

  /**
    * Parses variables of incoming query.
    *
    * @param variables variables from incoming query
    * @return JsObject with variables
    */
  def parseVariables(variables: String): JsObject =
    if (variables.trim.isEmpty || variables.trim == "null") Json.obj()
    else Json.parse(variables).as[JsObject]
}
