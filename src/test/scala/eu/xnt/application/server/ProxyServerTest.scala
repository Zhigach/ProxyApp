package eu.xnt.application.server

import akka.NotUsed
import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString
import eu.xnt.application.UnitTestSpec
import eu.xnt.application.stream.Connection
import eu.xnt.application.testutils.Util._

import java.nio.ByteBuffer
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class ProxyServerTest extends UnitTestSpec {

    override protected def beforeAll(): Unit = {

        val oldQuotes = Source(List(oldQuote(9, "OLD"), oldQuote(1, "OLD")))
          .map(q => ByteString(encodeQuoteWithLengthPrefix(q)))

        val quoteSource = Source(1 to 600).throttle(1, 1 second)
          .map(_ => randomQuote(ticker = "PS.TEST"))
          .map(q => ByteString(encodeQuoteWithLengthPrefix(q)))

        val quoteFlow: Flow[ByteString, ByteString, NotUsed] =
            Flow.fromSinkAndSource(
                Sink.ignore,
                oldQuotes concat quoteSource
            )

        val (tcpServer, serverSource) = Tcp().bind(connection.host, connection.port).preMaterialize()
        Await.ready(tcpServer, 10 seconds)
        serverSource.runForeach { connection =>
            connection.handleWith(quoteFlow)
        }
    }

    override protected def afterAll(): Unit = {
        testKit.system.terminate()
        testKit.shutdownTestKit()
        system.terminate()
        actorSystem.terminate()
    }

    val connection: Connection = Connection("localhost", 5555)

    val system: typed.ActorSystem[ProxyServer.CandleHistory] =
        typed.ActorSystem(ProxyServer(), "ProxyServerApp")
    val testKit: ActorTestKit = ActorTestKit("StreamReaderTest")
    val testProbe: TestProbe[String] = testKit.createTestProbe[String]("QuoteListener")

    implicit val actorSystem: ActorSystem = ActorSystem("ServerActorSystem")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = testKit.system.executionContext

    "ProxyServer" should "provide historical data to a newly connected client" in {
        testProbe.expectNoMessage(10 second)
        Http().singleRequest(HttpRequest(HttpMethods.GET, Uri("http://localhost:8080"))) onComplete {
            case Success(response) =>
                handleHttpResponse(response, testProbe)
            case Failure(exception) =>
                fail("Failed to receive response: {}", exception)
        }
        testProbe.expectMessage(10 seconds, "OLD")
        testProbe.expectMessage(10 seconds, "OLD")
    }

    it should "send new candles" in {
        val reqFuture = Http().singleRequest(HttpRequest(HttpMethods.GET, Uri("http://localhost:8080")))
        reqFuture.onComplete {
            case Success(response) =>
                handleHttpResponse(response, testProbe)
            case Failure(exception) =>
                fail("Failed to receive response: {}", exception)
        }
        testProbe.fishForMessage(65 seconds) {
            case "PS.TEST" => FishingOutcome.Complete
            case _ => FishingOutcome.Continue
        }
    }
}
