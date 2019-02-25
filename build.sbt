name := "play-sangria-graphql-subscriptions"

version := "1.0"

lazy val `graphql_api_example` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Atlassian Maven Repository" at "https://maven.atlassian.com/content/repositories/atlassian-public/"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  guice,
  "org.sangria-graphql" %% "sangria-play-json" % "1.0.5",
  "org.sangria-graphql" %% "sangria" % "1.4.2",
  "org.sangria-graphql" %% "sangria-akka-streams" % "1.0.1",
  "io.monix" %% "monix" % "2.3.3"
)