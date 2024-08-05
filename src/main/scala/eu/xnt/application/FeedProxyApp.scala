package eu.xnt.application

import akka.actor.typed.ActorSystem
import eu.xnt.application.repository.RepositoryActor
import eu.xnt.application.server.ProxyServer

object FeedProxyApp extends App {

    val proxyServer = ProxyServer()

    val system: ActorSystem[RepositoryActor.CandleHistory] =
        ActorSystem(ProxyServer(), "ProxyServerApp")
}
