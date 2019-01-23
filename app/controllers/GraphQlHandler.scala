package controllers

import play.api.libs.json._
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.util.Try

/**
  * Handles and parse GraphQL query.
  *
  * @param controllerComponents base controller components dependencies that most controllers rely on
  */
class GraphQlHandler(controllerComponents: ControllerComponents) extends AbstractController(controllerComponents) {

  /**
    * Parses JSON into components of GraphQL query
    *
    * @param json an instance of JsValue which will be being parsed
    * @return a tuple which contains GraphQL query components, namely query body, variables and operation
    */
  def parseToGraphQLQuery(json: JsValue): Try[(String, Option[String], Option[JsObject])] = {
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
    Try {
      json match {
        case arrayBody@JsArray(_) => extract(arrayBody.value(0))
        case objectBody@JsObject(_) => extract(objectBody)
        case otherType =>
          throw new Error {
            s"/graphql endpoint does not support request body of type [${otherType.getClass.getSimpleName}]"
          }
      }
    }
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
