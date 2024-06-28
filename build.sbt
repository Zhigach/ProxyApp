ThisBuild / version := "0.1.1-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")


val AkkaVersion = "2.5.23"
val AkkaHttpVersion = "10.1.8"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "io.spray" %% "spray-json" % "1.3.5", //do not upgrade
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % "3.2.19" % Test

)

lazy val root = (project in file("."))
  .settings(
    name := "FeedProxy"
  )
