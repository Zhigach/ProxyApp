ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

val AkkaVersion = "2.9.3"

libraryDependencies ++= Seq(
    "org.scala-lang" %% "toolkit" % "0.1.7",
    "com.google.code.gson" % "gson" % "2.11.0",
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,

    "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test

)

lazy val root = (project in file("."))
  .settings(
    name := "FeedProxy"
  )
