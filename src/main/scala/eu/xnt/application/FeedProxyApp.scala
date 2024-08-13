package eu.xnt.application

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import eu.xnt.application.server.ProxyServer

object FeedProxyApp extends App {

    val config = ConfigFactory.load("application.conf").withFallback(ConfigFactory.load())
    val system: ActorSystem[ProxyServer.CandleHistory] =
        ActorSystem(ProxyServer(config), "ProxyServerApp", config)

}
