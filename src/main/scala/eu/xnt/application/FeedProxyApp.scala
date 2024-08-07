package eu.xnt.application

import akka.actor.typed.ActorSystem
import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{AddQuote, RepositoryCommand}
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.server.ProxyServer

object FeedProxyApp extends App {

    val system: ActorSystem[RepositoryActor.CandleHistory] =
        ActorSystem(ProxyServer(), "ProxyServerApp")

    /*val testRepo: ActorSystem[RepositoryCommand] = ActorSystem(RepositoryActor(new InMemoryCandleRepository(60000)), "TestActor")

    testRepo ! AddQuote(Quote(
        timestamp = System.currentTimeMillis(),
        tickerLen = 4,
        ticker = "TEST",
        price = 100,
        size = 1
    ))*/
}
