package eu.xnt.application.repository

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{CandleHistoryRequest, RepositoryCommand}
import eu.xnt.application.server.ProxyServer.CandleHistory


object RepositoryActor extends LazyLogging {

    sealed trait RepositoryCommand
    case class AddQuote(q: Quote) extends RepositoryCommand
    case class CandleHistoryRequest(limit: Int = 1, replyTo: ActorRef[CandleHistory]) extends RepositoryCommand


    def apply(repository: InMemoryCandleRepository): Behavior[RepositoryCommand] = {
        Behaviors.setup( context => {
            val repositoryActor = new RepositoryActor(repository, context)
            repositoryActor.basicBehavior()
        })
    }
}

class RepositoryActor(repository: InMemoryCandleRepository, context: ActorContext[RepositoryCommand])
  extends LazyLogging {

    private def addQuote(quote: Quote): Unit =
        repository.addQuote(quote)

    private def basicBehavior(): Behavior[RepositoryCommand] = {
        Behaviors.receiveMessage {
            case RepositoryActor.AddQuote(q) =>
                logger.debug("Quote received: {}", q)
                addQuote(q)
                Behaviors.same
            case CandleHistoryRequest(limit, replyTo) =>
                val candles = repository.getHistoricalCandles(limit)
                replyTo ! CandleHistory(candles)
                Behaviors.same
        }
    }
}
