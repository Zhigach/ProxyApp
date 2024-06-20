package eu.xnt.application

import akka.actor.{ActorSystem, Props}
import eu.xnt.application.server.ProxyServer
import eu.xnt.application.stream.Command.*
import eu.xnt.application.stream.{ConnectionAddress, StreamReader}



object FeedProxyApp extends App {

    implicit val system: ActorSystem = ActorSystem("FeedProxyActorSystem")

    val proxyServer = ProxyServer

    /*val connection = ConnectionAddress("localhost", 5555)
    private val streamHandler = system.actorOf(Props.create(classOf[StreamHandler], connection), "StreamHandler")

    streamHandler ! Connect(connection)*/


}
