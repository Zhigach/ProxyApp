package eu.xnt.application

import akka.actor.{ActorSystem, Props}
import eu.xnt.application.stream.Command.*
import eu.xnt.application.repository.CandleRepositoryActor
import eu.xnt.application.stream.{ConnectionAddress, StreamHandler}


object FeedProxyApp extends App {

    val system = ActorSystem("FeedProxyActorSystem")

    val connection = ConnectionAddress("localhost", 5555)
    val streamHandler = system.actorOf(Props.create(classOf[StreamHandler], connection), "StreamHandler")

    //val candleRepositoryActor = system.actorOf(Props.create(classOf[CandleRepositoryActor]), "CandleRepositoryActor")

    streamHandler ! Connect(connection)


}
