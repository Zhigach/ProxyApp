package eu.xnt.application.server

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import eu.xnt.application.model.CandleModels.{Candle, CandleResponse, HistoryRequest}
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.{ConnectionAddress, StreamReader}
import eu.xnt.application.utils.Math.roundBy
import eu.xnt.application.model.JsonSupport.CandleJsonFormat
import spray.json.enrichAny

import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.Success


object ProxyServer {

    private val (endpoint, port) = ("localhost", 5555) // TODO make configuration file
    private val (serverAddress, bindPort) = ("localhost", 8080)
    private val candleDurationMillis = 60000
    private val initialHistoryDepth = 10


    implicit val system: ActorSystem = ActorSystem("ProxyServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val jsonEntityStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()


    private val repository = InMemoryCandleRepository(candleDurationMillis)

    private val repositoryActor = system.actorOf(Props.create(classOf[RepositoryActor], repository), "RepositoryActor")

    private val connection: ConnectionAddress = ConnectionAddress(endpoint, port)

    private val streamReaderActor = system.actorOf(Props.create(classOf[StreamReader], connection, repositoryActor), "StreamHandler")

    private val (sourceActorRef, source) =
        Source.actorRef[Candle](
            completionMatcher = {
                case Done =>
                    CompletionStrategy.immediately
                },
            failureMatcher = PartialFunction.empty,
            bufferSize = 100,
            overflowStrategy = OverflowStrategy.dropHead)
          .preMaterialize()

    source.runWith(Sink.ignore)

    private val refreshJob = system.scheduler.scheduleWithFixedDelay(
          initialDelay = {
              val currentTimeMillis = System.currentTimeMillis()
              val delayToFirstExecution = currentTimeMillis.roundBy(candleDurationMillis) + candleDurationMillis - currentTimeMillis
              Duration.create(delayToFirstExecution, TimeUnit.MILLISECONDS)
          },
          delay = FiniteDuration.apply(candleDurationMillis, TimeUnit.MILLISECONDS))
      (() => {
          implicit val timeout: Timeout = Timeout(10 seconds)
          val result = repositoryActor ? HistoryRequest()
          result onComplete {
              case Success(candles: CandleResponse) =>
                  for (can <- candles.candles) sourceActorRef ! can
          }
      })(system.dispatcher)


    private val routes: Route =
        get {
            concat(
                path("health") {
                    complete("Ok!")
                },
                path("candles") {
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

    private val bindingFuture = Http().newServerAt(serverAddress, bindPort).bind(routes)

}
