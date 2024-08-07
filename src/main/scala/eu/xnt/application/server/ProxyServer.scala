package eu.xnt.application.server

import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.{Connection, StreamReader}
import eu.xnt.application.utils.Math._
import eu.xnt.application.model.JsonSupport.CandleJsonFormat
import eu.xnt.application.repository.RepositoryActor.CandleHistoryRequest
import eu.xnt.application.server.ProxyServer.CandleHistory
import spray.json.enrichAny

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Success


object ProxyServer {

    final case class CandleHistory(candles: Vector[Candle])

    def apply(): Behavior[CandleHistory] = {
        Behaviors.setup { context =>
            new ProxyServer(context).ignoringBehavior()
        }
    }
}

class ProxyServer(context: ActorContext[CandleHistory]) extends LazyLogging {

    private val (endpoint, port) = ("localhost", 5555)
    private val (serverAddress, bindPort) = ("localhost", 8080)
    private val defaultCandleDurationMillis = 60000
    private val initialHistoryDepth = 10


    implicit val system: ActorSystem = ActorSystem("ProxyServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher //FIXME глобальный и акторный ec - зло
    implicit val materializer: Materializer = ActorMaterializer()


    private val repositoryActor =
        context.spawn(RepositoryActor(new InMemoryCandleRepository(defaultCandleDurationMillis)), "RepositoryActor")

    private val streamReaderActor = {
        context.spawn(StreamReader(Connection(endpoint, port), repositoryActor), "StreamReader")
    }

    private def ignoringBehavior(): Behavior[CandleHistory] = {
        Behaviors.same
    }

    private val (sourceActorRef, source) =
        Source.actorRef[Candle](
              bufferSize = 100,
              overflowStrategy = OverflowStrategy.dropHead)
          .preMaterialize()

    source.runWith(Sink.ignore)

    /**
     * Create a periodic task to request new candle at the start of a new timeframe and push it to the clients
     */
    system.scheduler.schedule(
        initialDelay = millisToNewTimeframe(),
        interval = defaultCandleDurationMillis millis
    ) {
        implicit val scheduler: Scheduler = system.scheduler
        implicit val timeout: Timeout = Timeout(10 seconds)
        val result = repositoryActor.ask(replyTo => CandleHistoryRequest(1, replyTo))
        result onComplete {
            case Success(candles: CandleHistory) =>
                for (can <- candles.candles) sourceActorRef ! can
        }
    }


    /**
     * Server routes
     */
    private val routes: Route =
        get {
            path("") {
                implicit val scheduler: Scheduler = system.scheduler
                implicit val timeout: Timeout = Timeout(10 seconds)

                val candleCacheFuture: Future[CandleHistory] =
                    repositoryActor.ask(replyTo => CandleHistoryRequest(initialHistoryDepth, replyTo))

                onComplete(candleCacheFuture) {
                    case Success(candles) =>
                        complete(
                            HttpEntity(
                                ContentTypes.`application/json`,
                                (Source(candles.candles) ++ source)
                                  .map(can => ByteString(can.toJson.compactPrint + '\n'))
                            )
                        )
                }
            }
        }

    Http().bindAndHandle(routes, interface = serverAddress, port = bindPort)

}
