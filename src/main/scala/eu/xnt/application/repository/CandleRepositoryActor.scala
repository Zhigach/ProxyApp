package eu.xnt.application.repository

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.CandleModels.{Candle, CandleRequest, CandleResponse}
import eu.xnt.application.CandleModels.Candle
import eu.xnt.application.Quote
import eu.xnt.application.model.Ticker

class CandleRepositoryActor extends Actor with ActorLogging {

    private val repo: InMemoryCandleRepository = InMemoryCandleRepository()

    private def addQuote(quote: Quote): Unit =
        repo.addQuote(quote)

    private def getLastCandle(ticker: Ticker, limit: Int): Array[Candle] =
        repo.getLastCandle(ticker, limit)

    override def receive: Receive = {
        case q: Quote =>
            log.info("Saving quote {}", q)
            addQuote(q) //FIXME race conditions occur when to close quotes are received
        case CandleRequest(ticker, limit) =>
            val candles = getLastCandle(ticker, limit)
            sender() ! CandleResponse(candles)
        case _ =>
            log.debug("Unsupported message received")
    }
}
