package eu.xnt.application.server

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.model.HttpMethods.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCode, Uri}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import eu.xnt.application.FeedProxyApp.system
import eu.xnt.application.model.CandleModels.{Candle, HistoryRequest, TickerCandlesRequest}
import eu.xnt.application.repository.{CandleBuffer, InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.Command.Connect
import eu.xnt.application.stream.{ConnectionAddress, StreamReader}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


object ProxyServer {

    implicit val system: ActorSystem = ActorSystem("ProxyServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val jsonEntityStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()



    private val repository = InMemoryCandleRepository()

    private val repositoryActor = system.actorOf(Props.create(classOf[RepositoryActor], repository), "RepositoryActor")

    private val connection: ConnectionAddress = ConnectionAddress("localhost", 5555)
    private val streamReaderActor = system.actorOf(Props.create(classOf[StreamReader], connection, repositoryActor), "StreamHandler")

    private val routes: Route =
        get {
            concat(
                pathSingleSlash {
                    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<html><body>Candle service home</body></html>"))
                },
                path("health") {
                    complete("Ok!")
                },
                path("candles") {
                    import eu.xnt.application.model.JsonSupport.CandleJsonFormat
                    val candles: Source[Candle, NotUsed] = Source(repository.getIterableBuffer(10))
                    complete(candles) //TODO no stream given, only JSON array. Probably static link to storage must be provided
                }
            )
        }

    private val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    streamReaderActor ! Connect(connection)
}
