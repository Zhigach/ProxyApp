package eu.xnt.application.server

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import eu.xnt.application.repository.testutils.Util
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class ProxyServerTest extends TestKit(ActorSystem("ProxyServerTest"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

    private val proxyServer = ProxyServer

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()


    private val oldQuotes = Source(List(Util.oldQuote(10, "OLD"), Util.oldQuote(5, "OLD")))
      .map(q => ByteString(Util.encodeQuote(q)))

    private val quoteSource = Source(1 to 600).throttle(1, 1 second)
      .map(_ => Util.randomQuote())
      .map(q => ByteString(Util.encodeQuote(q)))

    val quoteFlow: Flow[ByteString, ByteString, NotUsed] =
        Flow.fromSinkAndSource(
            Sink.ignore,
            oldQuotes concat quoteSource
        )

    Tcp()
      .bind("localhost", 5555)
      .runForeach {
          connection => connection.handleWith(quoteFlow)
      }


    "ProxyServer" must {

        val logProbe = TestProbe()
        proxyServer.system.eventStream.subscribe(logProbe.ref, classOf[Logging.LogEvent] )

        "connect to a source feed" in {
            logProbe.fishForMessage(10 seconds) {
                case Logging.Info(_, _, msg: String) if msg.contains("Stream connected") => true
                case _ => false
            }
        }

        "receive quotes" in {
            expectNoMessage(10 seconds)
        }


        "provide historical data to a newly connected client" in {

            val reqFuture = Http().singleRequest(HttpRequest(HttpMethods.GET, Uri("http://localhost:8080")))
            reqFuture.onComplete {
                case Success(response) =>
                    response.entity.dataBytes.runForeach { chunk: ByteString =>
                        val tickerRegexp = """"ticker":"(.*?)"""".r
                        val ticker = tickerRegexp.findFirstMatchIn(chunk.utf8String)
                        ticker match {
                            case Some(tickerName) => logProbe.send(logProbe.ref, tickerName.group(1))
                            case None => fail
                        }

                    }
                case Failure(error) =>
                    println(s"Request failed: $error")
            }

            logProbe.expectMsg(10 seconds, "OLD")
        }

        "send new candle at the start of a new minute" in {
            logProbe.fishForMessage(65 seconds) {
                case "TEST" => true
                case _ => false
            }
        }

    }

    override protected def afterAll(): Unit = {
        TestKit.shutdownActorSystem(system)
    }

}
