package controllers

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import graphql.GraphQL
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
  def props(outActor: ActorRef, graphQL: GraphQL, controllerComponents: ControllerComponents)
           (implicit ec: ExecutionContext, mat: Materializer): Props = {
    Props(new WebSocketFlowActor(outActor, graphQL, controllerComponents))
  }
}

class WebSocketFlowActor(outActor: ActorRef,
                         graphQL: GraphQL,
                         controllerComponents: ControllerComponents)
                        (implicit ec: ExecutionContext,
                         mat: Materializer)
  extends GraphQlHandler(controllerComponents) with Actor {

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