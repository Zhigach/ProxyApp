package eu.xnt.application.repository

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.model.CandleModels.{Candle, CandleRequest, CandleResponse}
import eu.xnt.application.model.Quote

import scala.collection.mutable.ArrayBuffer

class QuoteReceiverActor extends Actor with ActorLogging {

    private val repo: InMemoryCandleRepository = InMemoryCandleRepository()

    private def addQuote(quote: Quote): Unit =
        synchronized {repo.addQuote(quote)}

    private def getLastCandle(ticker: String, limit: Int): Array[Candle] =
        repo.getLastCandle(ticker, limit)

    override def receive: Receive = {
        case q: Quote =>
            log.info("Saving quote {}", q)
            addQuote(q)
        case CandleRequest(ticker, limit) =>
            val candles = getLastCandle(ticker, limit)
            sender() ! CandleResponse(candles)
        case _ =>
            log.debug("Unsupported message received")
    }
}
