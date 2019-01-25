package graphql

case class UserContext(maybeGraphQlSubs: Option[GraphQLSubscriptions] = None)