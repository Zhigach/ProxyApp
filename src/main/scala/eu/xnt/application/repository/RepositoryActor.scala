package eu.xnt.application.repository

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.model.CandleModels.{CandleResponse, HistoryRequest}
import eu.xnt.application.model.Quote


class RepositoryActor(repository: InMemoryCandleRepository) extends Actor with ActorLogging {

    private def addQuote(quote: Quote): Unit =
        synchronized { repository.addQuote(quote) }

    /**
     * Basic actor behaviour. Receives only two types of messages 1) new quote (adds it to storage);
     * 2) HistoryRequest for candles
     */
    override def receive: Receive = {
        case q: Quote =>
            addQuote(q)
        case HistoryRequest(limit) =>
            val candles = repository.getHistoricalCandles(limit)
            sender() ! CandleResponse(candles)
        case _ =>
            log.debug("Unsupported message received")
    }
}
