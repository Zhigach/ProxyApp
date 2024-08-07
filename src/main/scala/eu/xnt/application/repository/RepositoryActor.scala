package eu.xnt.application.repository

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{CandleHistoryRequest, CandleHistory, RepositoryCommand}


object RepositoryActor extends LazyLogging {

    sealed trait RepositoryCommand
    case class AddQuote(q: Quote) extends RepositoryCommand
    case class CandleHistoryRequest(limit: Int = 1, replyTo: ActorRef[CandleHistory]) extends RepositoryCommand
    case class CandleHistory(candles: Vector[Candle]) extends RepositoryCommand


    def apply(repository: InMemoryCandleRepository): Behavior[RepositoryCommand] = {
        Behaviors.setup( context => {
            val repositoryActor = new RepositoryActor(repository, context)
            repositoryActor
        })
    }
}

class RepositoryActor(repository: InMemoryCandleRepository, context: ActorContext[RepositoryCommand])
  extends AbstractBehavior[RepositoryCommand]
    with LazyLogging {

    private def addQuote(quote: Quote): Unit =
        repository.addQuote(quote)


    override def onMessage(msg: RepositoryCommand): Behavior[RepositoryCommand] = {
        msg match {
            case RepositoryActor.AddQuote(q) =>
                logger.debug("Quote received: {}", q)
                addQuote(q)
                this
            case CandleHistoryRequest(limit, replyTo) =>
                val candles = repository.getHistoricalCandles(limit)
                replyTo ! CandleHistory(candles)

                this
            case CandleHistory(candles) =>
                //TODO remove this case. CandleResponse is excessive for the RepositoryCommand trait
                this
        }
    }
}
