package graphql

/**
  * A class which used in GraphQl context
  *
  * @param graphQlSubs an instance of 'GraphQLSubscriptions' class which contains a list
  *                    of subscriptions which was opened during one WebSocket connection
  *                    by a user and which can be canceled on demand.
  */
case class UserContext(graphQlSubs: Option[GraphQLSubscriptions] = None)