package eu.xnt.application.repository

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.model.CandleModels.{Candle, CandleResponse, HistoryRequest, TickerCandlesRequest}
import eu.xnt.application.model.Quote


class RepositoryActor(repository: InMemoryCandleRepository) extends Actor with ActorLogging {   

    private def addQuote(quote: Quote): Unit =
        synchronized { repository.addQuote(quote) }

    private def getLastCandle(ticker: String, limit: Int): Array[Candle] =
        repository.getLastCandle(ticker, limit)

    override def receive: Receive = {
        case q: Quote =>
            addQuote(q)
        case TickerCandlesRequest(ticker, limit) =>
            val candles = getLastCandle(ticker, limit)
            sender() ! CandleResponse(candles)
        case HistoryRequest(limit) =>
            val candles = repository.getHistoricalCandles(limit)
            sender() ! CandleResponse(candles)
        case _ =>
            log.debug("Unsupported message received")
    }
}
