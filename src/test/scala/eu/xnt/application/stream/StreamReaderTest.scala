package eu.xnt.application.stream

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Props}
import akka.stream.{ActorMaterializer, FlowShape, Graph, KillSwitch, KillSwitches, UniqueKillSwitch}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.testkit.{EventFilter, TestKit, TestProbe}
import akka.util.ByteString
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.testutils.Util
import eu.xnt.application.repository.{InMemoryCandleRepository, RepositoryActor}
import eu.xnt.application.stream.StreamReader.ReadStream
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

    val probe = TestProbe()

    //val q: Quote = Quote(System.currentTimeMillis(), 4, "AAPL", 100.0, 101)

    val serverSource: Source[Tcp.IncomingConnection, Future[Tcp.ServerBinding]] = Tcp().bind("localhost", 5555)

    val quotes: Source[ByteString, _] = Source.tick(1.second, 1.second, ())
      .map(_ => Quote(System.currentTimeMillis(), 4, "AAPL", 100.0, 101)) //q)
      .map(Util.encodeQuote)
      .map { bytes =>
          val lengthPrefix = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(bytes.length.toShort).array()
          ByteString(lengthPrefix) ++ ByteString(bytes)
      }


    val quoteSource = Source(1 to 2).throttle(1, 1 second)
      .map(_ => Quote(System.currentTimeMillis(), 4, "AAPL", 100.0, 101)) //q)
      .map(Util.encodeQuote)
      .map { bytes =>
          val lengthPrefix = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(bytes.length.toShort).array()
          ByteString(lengthPrefix) ++ ByteString(bytes)
      }

    val quoteFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(Sink.ignore, quoteSource)

    val observeRepository = InMemoryCandleRepository(60000)
    val repositoryActor = system.actorOf(Props.create(classOf[RepositoryActor], observeRepository), "RepositoryActor")
    val connection: ConnectionAddress = ConnectionAddress("localhost", 5555)
    val streamReaderActor = system.actorOf(Props.create(classOf[StreamReader], connection, repositoryActor), "StreamReader")

    "transition to connected state after successful connection" in {
        val probe = TestProbe()
        val mockConnection = TestProbe()

        val streamReader = system.actorOf(Props(new StreamReader(mockConnection.ref, probe.ref)))

        // Simulate a successful connection
        val inputStream = new ByteArrayInputStream("test data".getBytes)
        mockConnection.send(streamReader, inputStream)

        // Expect a ReadStream message to be sent to itself
        probe.expectMsg(ReadStream)

        // Optionally, verify the log message
        EventFilter.info(s"Stream connected at ...", occurrences = 1) intercept {
            // ... (rest of your test logic)
        }
    }

    "StreamReader" must {

        "connect to endpoint that was not available at the start" in {
            serverSource.runForeach { connection =>
                connection.handleWith(quoteFlow)
            }

            within(StreamReader.reconnectPeriod * 2) {
                expectNoMessage()
                assert(observeRepository.bufferSize("AAPL") == 1)
            }

        }

        "try reconnecting in case of stream failure" in {
            //killSwitch.shutdown()
            expectNoMessage(10 seconds)
        }

        "reconnect in case endpoint is alive again" in {
            //Http().bindAndHandle(handler = route, "localhost", 5555)

        }

    }


    override def afterAll(): Unit = {

        TestKit.shutdownActorSystem(system)
    }
}
