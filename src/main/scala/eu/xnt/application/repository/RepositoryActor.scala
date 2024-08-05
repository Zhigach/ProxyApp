package eu.xnt.application.repository

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{CandleHistoryRequest, CandleHistory, RepositoryCommand}


class RepositoryActor(repository: InMemoryCandleRepository, context: ActorContext[RepositoryCommand])
  extends AbstractBehavior[RepositoryCommand]
    with LazyLogging {

    private def addQuote(quote: Quote): Unit =
        repository.addQuote(quote)

    logger.info("Repository actor created")

    /**
     * Basic actor behaviour. Receives only two types of messages 1) new quote (adds it to storage);
     * 2) HistoryRequest for candles
     */
    /*override def receive: Receive = {
        case q: Quote =>
            addQuote(q)
        case CandleHistoryRequest(limit) =>
            val candles = repository.getHistoricalCandles(limit)
            sender() ! CandleHistoryResponse(candles)
        case _ =>
            logger.debug("Unsupported message received")
    }*/

    override def onMessage(msg: RepositoryCommand): Behavior[RepositoryCommand] = {
        msg match {
            case RepositoryActor.AddQuote(q) =>
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

object RepositoryActor {

    sealed trait RepositoryCommand
    case class AddQuote(q: Quote) extends RepositoryCommand
    case class CandleHistoryRequest(limit: Int = 1, replyTo: ActorRef[CandleHistory]) extends RepositoryCommand
    case class CandleHistory(candles: Vector[Candle]) extends RepositoryCommand


    def apply(repository: InMemoryCandleRepository): Behavior[RepositoryCommand] = {
        Behaviors.setup( context => new RepositoryActor(repository, context))
    }
}
