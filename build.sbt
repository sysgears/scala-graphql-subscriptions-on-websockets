name := "play-sangria-graphql-subscriptions"

version := "1.0"

lazy val `graphql_api_example` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Atlassian Maven Repository" at "https://maven.atlassian.com/content/repositories/atlassian-public/"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  evolutions,
  guice,
  "org.sangria-graphql" %% "sangria-play-json" % "1.0.5",
  "org.sangria-graphql" %% "sangria" % "1.4.2",
  "org.sangria-graphql" %% "sangria-akka-streams" % "1.0.1",
  "io.monix" %% "monix" % "2.3.3",

  "com.h2database" % "h2" % "1.4.197",
  
  "com.typesafe.play" %% "play-slick" % "4.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "4.0.0",
  "com.typesafe.slick" %% "slick" % "3.3.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0"
)