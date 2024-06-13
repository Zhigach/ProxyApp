package eu.xnt.application.adapter

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import eu.xnt.application.CandleModels.{Candle, CandleRequest, CandleResponse}
import eu.xnt.application.CandleModels.CandleResponse
import eu.xnt.application.Quote
import eu.xnt.application.model.Ticker
import eu.xnt.application.repository.CandleRepositoryActor

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Success

class QuoteProcessor extends QuoteReceiverActor {

    private val candleReceiver = context.actorOf(Props[CandleRepositoryActor](), "CandleRepositoryActor")

    override def receive: Receive = {
        case quote: Quote => // some additional logic can be here
            import scala.concurrent.duration._
            import concurrent.ExecutionContext.Implicits.global
            candleReceiver ! quote

            //FIXME: just a test - remove
            /*val askForCandles: Future[CandleResponse] =
                (candleReceiver ? CandleRequest(Ticker("GOOG"), 2))(5 seconds).mapTo[CandleResponse]
            askForCandles onComplete {
                case Success(value) =>
                    candlesReceived(value)
                case scala.util.Failure(_) =>
                    log.error("Failed to get candles")
            }*/
        case _ =>
            log.error("Unsupported message received")
    }

    private def candlesReceived(candleResponse: CandleResponse): Unit =
        val msg = (for (c <- candleResponse.candles) yield ("\t"+c.toString)).mkString("\n")
        log.info("Received candles:\n{}", msg)
}
