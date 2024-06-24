package eu.xnt.application.server

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.ByteString
import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.Command.Connect
import eu.xnt.application.stream.{ConnectionAddress, StreamReader}
import spray.json.enrichAny

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.*
import scala.language.postfixOps


object ProxyServer {

    implicit val system: ActorSystem = ActorSystem("ProxyServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val jsonEntityStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

    private val candleDurationMillis = 60000
    private val initialHistoryDepth = 10

    private val repository = InMemoryCandleRepository(candleDurationMillis) //TODO make it elegant

    private val repositoryActor = system.actorOf(Props.create(classOf[RepositoryActor], repository), "RepositoryActor")

    private val connection: ConnectionAddress = ConnectionAddress("localhost", 5555)

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
              val delayToFirstExecution = ((currentTimeMillis / candleDurationMillis) + 1) * candleDurationMillis - currentTimeMillis
              Duration.create(delayToFirstExecution, TimeUnit.MILLISECONDS)
          },
          delay = FiniteDuration.apply(candleDurationMillis, TimeUnit.MILLISECONDS))
      (() => {
          for (can <- repository.getHistoricalCandles(1)) sourceActorRef ! can
      })(system.dispatcher)

    private val routes: Route =
        get {
            concat(
                path("health") {
                    complete("Ok!")
                },
                path("candles") {
                    import eu.xnt.application.model.JsonSupport.CandleJsonFormat
                    val candleCache = Source(repository.getHistoricalCandles(initialHistoryDepth))
                    complete(
                        HttpEntity(
                            ContentTypes.`application/json`,
                            (candleCache ++ source)
                              .map(can => ByteString(can.toJson.compactPrint + /*->*/'\n'/*<-*/)) //TODO узнать почему мне приходится делать это говно
                        )
                    )
                }
            )
        }

    private val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    streamReaderActor ! Connect(connection)

}
