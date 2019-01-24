package graphql

import com.google.inject.Inject
import graphql.schemas.PostSchema
import sangria.schema.{ObjectType, fields}

/**
  * Base component for the GraphQL schema.
  *
  * @param postSchema an object containing all queries, mutations and subscriptions to work with the Post entity
  */
class GraphQL @Inject()(postSchema: PostSchema) {

  /**
    * Contains a graphql schema of the entire application.
    * We can add queries, mutations, etc. for each model.
    */
  val Schema = sangria.schema.Schema(
    query = ObjectType(
      "Query",
      fields(
        postSchema.Queries: _*
      )
    ),
    mutation = Some(
      ObjectType(
        "Mutation",
        fields(
          postSchema.Mutations: _*
        )
      )
    ),
    subscription = Some(
      ObjectType(
        "Subscription",
        fields(
          postSchema.Subscriptions: _*
        )
      )
    )
  )
}