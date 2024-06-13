ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

val AkkaVersion = "2.9.3"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "FeedProxy"
  )
