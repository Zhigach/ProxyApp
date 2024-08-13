package eu.xnt.application.server

import akka.NotUsed
import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString
import eu.xnt.application.UnitTestSpec
import eu.xnt.application.stream.Connection
import eu.xnt.application.testutils.Util._
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.JsonParser

import java.nio.ByteBuffer
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class ProxyServerTest extends UnitTestSpec {

    val connection: Connection = Connection("localhost", 5555)
    private val testKit = ActorTestKit("StreamReaderTest")

    val system: typed.ActorSystem[ProxyServer.CandleHistory] =
        typed.ActorSystem(ProxyServer(), "ProxyServerApp")

    implicit val actorSystem = ActorSystem("ServerActorSystem")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = testKit.system.executionContext


    override protected def beforeAll(): Unit = {

        val oldQuotes = Source(List(oldQuote(9, "OLD"), oldQuote(1, "OLD")))
          .map(q => ByteString(ByteBuffer.allocate(2).putShort(q.len).array() ++ encodeQuote(q)))

        val quoteSource = Source(1 to 600).throttle(1, 1 second)
          .map(_ => randomQuote(ticker = "PS.TEST"))
          .map(q => ByteString(ByteBuffer.allocate(2).putShort(q.len).array() ++ encodeQuote(q)))

        val quoteFlow: Flow[ByteString, ByteString, NotUsed] =
            Flow.fromSinkAndSource(
                Sink.ignore,
                oldQuotes concat quoteSource
            )

        Tcp()
          .bind(connection.host, connection.port)
          .runForeach {
              connection => connection.handleWith(quoteFlow)
          }
    }

    val testProbe = testKit.createTestProbe[String]("QuoteListener")

    "ProxyServer" should "provide historical data to a newly connected client" in {
        testProbe.expectNoMessage(10 second)
        Http().singleRequest(HttpRequest(HttpMethods.GET, Uri("http://localhost:8080"))) onComplete {
            case Success(response) =>
                response.entity.dataBytes.runForeach { chunk: ByteString =>
                    val json = JsonParser(chunk.utf8String)
                    val ticker = json.asJsObject.fields("ticker").convertTo[String]
                    testProbe.ref ! ticker
                }
            case Failure(exception) => fail("Failed to receive response: {}", exception)
        }
        testProbe.expectMessage(10 seconds, "OLD")
        testProbe.expectMessage(10 seconds, "OLD")
    }

    it should "send new candles" in {
        val reqFuture = Http().singleRequest(HttpRequest(HttpMethods.GET, Uri("http://localhost:8080")))
        reqFuture.onComplete {
            case Success(response) =>
                response.entity.dataBytes.runForeach { chunk: ByteString =>
                    val json = JsonParser(chunk.utf8String)
                    val ticker = json.asJsObject.fields("ticker").convertTo[String]
                    testProbe.ref ! ticker
                }
            case Failure(error) =>
                println(s"Request failed: $error")
        }
        testProbe.fishForMessage(65 seconds) {
            case "PS.TEST" => FishingOutcome.Complete
            case _ => FishingOutcome.Continue
        }
    }


    override protected def afterAll(): Unit = {
        testKit.shutdownTestKit()
        system.terminate()
        actorSystem.terminate()
    }

}
