package eu.xnt.application.server

import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import com.typesafe.config.{Config, ConfigFactory}
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
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.DurationConverters.JavaDurationOps
import scala.language.postfixOps
import scala.util.{Failure, Success}


object ProxyServer {

    final case class CandleHistory(candles: Vector[Candle])

    def apply(config: Config): Behavior[CandleHistory] = {
        Behaviors.setup { context =>
            new ProxyServer(context, config).ignoringBehavior()
        }
    }
}

class ProxyServer(context: ActorContext[CandleHistory], config: Config) extends LazyLogging {

    private val (endpoint, port) = (config.getString("proxy-server.connector.endpoint"),
                                    config.getInt("proxy-server.connector.port"))
    private val (serverAddress, bindPort) = (config.getString("proxy-server.server.serverAddress"),
                                                config.getInt("proxy-server.server.bindPort"))
    private val defaultCandleDurationMillis = config.getInt("proxy-server.server.defaultCandleDurationMillis")
    private val initialHistoryDepth = config.getInt("proxy-server.server.initialHistoryDepth")
    private val reconnectPeriod = config.getDuration("proxy-server.connector.reconnectPeriod").toScala


    implicit val system: ActorSystem = ActorSystem("ProxyServer", config)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = ActorMaterializer()


    private val repositoryActor =
        context.spawn(RepositoryActor(new InMemoryCandleRepository(defaultCandleDurationMillis)), "RepositoryActor")

    private val streamReaderActor = {
        context.spawn(StreamReader(Connection(endpoint, port), reconnectPeriod, repositoryActor), "StreamReader")
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
            case Failure(exception) =>
                logger.error("Failed to retrieve Historical Candles", exception)
        }
    }

    implicit def exceptionHandler: ExceptionHandler =
        ExceptionHandler {
            case _: ArithmeticException =>
                extractUri { uri =>
                    println(s"Request to $uri could not be handled normally")
                    complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
                }
        }

    /**
     * Server routes
     */
    private val routes: Route = Route.seal(
        get {
            path("") {
                implicit val scheduler: Scheduler = system.scheduler
                implicit val timeout: Timeout = Timeout(10 seconds)

                val candleCacheFuture: Future[CandleHistory] =
                    repositoryActor.ask(replyTo => CandleHistoryRequest(initialHistoryDepth, replyTo))
                onSuccess(candleCacheFuture) { candles =>
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
    )


    Http().bindAndHandle(routes, interface = serverAddress, port = bindPort)

}
