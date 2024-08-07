package eu.xnt.application

import akka.actor.typed.ActorSystem
import eu.xnt.application.server.ProxyServer

object FeedProxyApp extends App {

    val system: ActorSystem[ProxyServer.CandleHistory] =
        ActorSystem(ProxyServer(), "ProxyServerApp")

}
