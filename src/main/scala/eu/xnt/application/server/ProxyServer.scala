package eu.xnt.application.server

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import eu.xnt.application.model.CandleModels.{Candle, CandleResponse, HistoryRequest}
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.{ConnectionAddress, StreamReader}
import eu.xnt.application.utils.Math._
import eu.xnt.application.model.JsonSupport.CandleJsonFormat
import spray.json.enrichAny

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.Success


object ProxyServer {

    private val (endpoint, port) = ("localhost", 5555) // TODO make configuration file
    private val (serverAddress, bindPort) = ("localhost", 8080)
    private val defaultCandleDurationMillis = 60000
    private val initialHistoryDepth = 10


    implicit val system: ActorSystem = ActorSystem("ProxyServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = ActorMaterializer()


    private val repository = InMemoryCandleRepository(defaultCandleDurationMillis)

    private val repositoryActor = system.actorOf(Props.create(classOf[RepositoryActor], repository), "RepositoryActor")

    private val connection: ConnectionAddress = ConnectionAddress(endpoint, port)

    private val streamReaderActor = system.actorOf(Props.create(classOf[StreamReader], connection, repositoryActor), "StreamReaderActor")

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
        interval = FiniteDuration.apply(defaultCandleDurationMillis, TimeUnit.MILLISECONDS)
    ) {
        val result = (repositoryActor ? HistoryRequest())(Timeout(10 seconds))
        result onComplete {
            case Success(candles: CandleResponse) =>
                for (can <- candles.candles) sourceActorRef ! can
        }
    }


    /**
     * Server routes
     */
    private val routes: Route =
        get {
            concat(
                path("") {
                    implicit val timeout: Timeout = Timeout(10 seconds)
                    val candleCacheFuture: Future[CandleResponse] = (repositoryActor ? HistoryRequest(initialHistoryDepth))
                      .asInstanceOf[Future[CandleResponse]]
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
            )
        }

    Http().bindAndHandle(routes, interface = serverAddress, port = bindPort)

}
