package eu.xnt.application.stream

import akka.{Done, NotUsed, testkit}
import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.stream.{ActorMaterializer, FlowShape, Graph, KillSwitch, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.testkit.{EventFilter, TestEvent, TestKit, TestProbe}
import akka.util.ByteString
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.testutils.Util
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.StreamReader.{Connect, ReadStream}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.ByteArrayInputStream
import java.nio.{ByteBuffer, ByteOrder}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class StreamReaderTest extends TestKit(ActorSystem("StreamReaderTest"))
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    private val q: Quote = Quote(System.currentTimeMillis(), 4, "AAPL", 100.0, 101)

    private val quoteSource = Source(1 to 4).throttle(1, 1 second)
      .map(_ => q)
      .map(Util.encodeQuote)
      .map { bytes =>
          val lengthPrefix = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(bytes.length.toShort).array()
          ByteString(lengthPrefix) ++ ByteString(bytes)
      }

    val quoteFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(Sink.ignore, quoteSource)

    Tcp()
      .bind("localhost", 5555)
      .runForeach {
          connection => connection.handleWith(quoteFlow)
      }

    val connection: ConnectionAddress = ConnectionAddress("localhost", 5555)



    "StreamReader" must {

        val quoteReceiverMock = TestProbe()
        val logProbe = TestProbe()
        system.eventStream.subscribe(logProbe.ref, classOf[Logging.LogEvent])

        val streamReader = system.actorOf(Props(new StreamReader(connection, quoteReceiverMock.ref)), "StreamReader")

        "connects and starts receiving quotes" in {
            quoteReceiverMock.expectMsgClass(10 seconds, classOf[Quote])
        }
        "stream fails" in {
            logProbe.fishForMessage(10 seconds) {
                case Logging.Error(_, _, _, msg: String) if msg.contains("Stream failed") => true
                case _ => false
            }
        }
        "reader reconnects" in {
            logProbe.fishForMessage(10 seconds) {
                case Logging.Info(_, _, msg: String) if msg.contains("Stream connected") => true
                case _ => false
            }
        }
    }


    override def afterAll(): Unit = {
        //system.eventStream.unsubscribe()
        TestKit.shutdownActorSystem(system)
    }
}
