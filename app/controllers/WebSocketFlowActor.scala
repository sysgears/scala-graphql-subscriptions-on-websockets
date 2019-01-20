package controllers

import akka.actor.{Actor, ActorRef, Props}
import graphql.GraphQL
import monix.execution.Scheduler
import monix.reactive.Observable
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import sangria.ast.Document
import sangria.ast.OperationType.Subscription
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.playJson._
import sangria.parser.QueryParser

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object WebSocketFlowActor {
  def props(outActor: ActorRef, graphQL: GraphQL, controllerComponents: ControllerComponents)
           (implicit ec: ExecutionContext, scheduler: Scheduler): Props = {
    Props(new WebSocketFlowActor(outActor, graphQL, controllerComponents))
  }
}

class WebSocketFlowActor(outActor: ActorRef, graphQL: GraphQL,
                         controllerComponents: ControllerComponents)
                        (implicit ec: ExecutionContext, scheduler: Scheduler)
  extends GraphQlHandler(controllerComponents) with Actor {

  override def receive: Receive = {
    case message: String =>

      val maybeQuery = Try(Json.parse(message)) match {
        case Success(json) => parseToGraphQLQuery(json)
        case Failure(error) => throw new Error(s"Fail to parse a request body. Reason [$error]")
      }

      val executedQuery: Observable[JsValue] = maybeQuery match {
        case Success((query, operationName, variables)) => executeQuery(query, graphQL, variables, operationName)
        case Failure(error) => Observable.now(JsString(error.getMessage))
      }

      executedQuery.foreach(m => outActor ! m.toString)
  }

  def executeQuery(query: String,
                   graphQL: GraphQL,
                   variables: Option[JsObject] = None,
                   operation: Option[String] = None)
                  (implicit scheduler: Scheduler): Observable[JsValue] = QueryParser.parse(query) match {

    case Success(queryAst: Document) =>
      queryAst.operationType(operation) match {
        case Some(Subscription) =>
          import sangria.execution.ExecutionScheme.Stream
          import sangria.streaming.monix._
          Executor.execute(
            schema = graphQL.Schema,
            queryAst = queryAst,
            variables = variables.getOrElse(Json.obj()),
          ).onErrorRecover {
            case error: QueryAnalysisError => Json.obj("BadRequest" -> error.resolveError)
            case error: ErrorWithResolver => Json.obj("InternalServerError" -> error.resolveError)
          }
        case _ => Observable.now {
          Json.obj("UnsupportedType" -> JsString(s"$operation"))
        }
      }

    case Failure(ex) => Observable.now(Json.obj("BadRequest" -> JsString(s"${ex.getMessage}")))
  }
}