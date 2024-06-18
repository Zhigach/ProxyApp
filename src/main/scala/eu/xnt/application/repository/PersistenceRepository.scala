package eu.xnt.application.repository

import akka.actor.ActorLogging
import akka.persistence.*
import eu.xnt.application.model.Quote
import eu.xnt.application.model.CandleModels.Candle

class PersistenceRepository extends PersistentActor with ActorLogging {

    override def persistenceId: String = "persistent-quote-repository"

    private val repo: InMemoryCandleRepository = InMemoryCandleRepository()

    private def addQuote(quote: Quote): Unit =
        println("Adding quote")
        repo.addQuote(quote)

    private def getLastCandle(ticker: String, limit: Int): Array[Candle] =
        repo.getLastCandle(ticker, limit)

    override def receiveRecover: Receive = {
        case q: Quote => log.info("quote in receiveRecover received")
        case _ => log.info("Recover not implemented")
    }

    override def receiveCommand: Receive = {
        case q: Quote =>
            log.info("Received quote")
            persist(q) { event =>
                  context.system.eventStream.publish(event)
                  addQuote(q)
            }
    }
}
