package eu.xnt.application.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, ThrottleMode}
import akka.util.ByteString
import eu.xnt.application.UnitTestSpec
import eu.xnt.application.repository.RepositoryActor
import eu.xnt.application.testutils.Util._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps


class StreamReaderTest extends UnitTestSpec {

    implicit val actorSystem = ActorSystem("ServerActorSystem")

    override protected def beforeAll(): Unit = {

        implicit val materializer: ActorMaterializer = ActorMaterializer()

        val quoteSource =
            Source(1 to 2).throttle(1, 1 second, 1, ThrottleMode.Shaping).initialDelay(0 second)
              .map(_ => randomQuote(System.currentTimeMillis()))
              .map(q => ByteString(encodeQuoteWithLengthPrefix(q)))

        val quoteFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(Sink.ignore, quoteSource)

        val (tcpServer, serverSource) = Tcp().bind(connection.host, connection.port).preMaterialize()
        Await.ready(tcpServer, 10 seconds)

        serverSource.runForeach { connection =>
            connection.handleWith(quoteFlow)
        }

    }

    override def afterAll(): Unit = {
        testKit.shutdownTestKit()
        testKit.system.terminate()
        actorSystem.terminate()
    }

    val connection: Connection = Connection("localhost", 5554)
    private val testKit = ActorTestKit("StreamReaderTest")

    private val quoteReceiverMock = testKit.createTestProbe[RepositoryActor.RepositoryCommand]("TestProbe")
    private val streamReader = testKit.spawn(StreamReader(connection, 5 seconds, quoteReceiverMock.ref), "StreamReader")

    "StreamReader" should "connect and start receiving quotes" in {
        quoteReceiverMock.expectMessageType[RepositoryActor.AddQuote](7 seconds)
    }

    it should "reconnect after stream fails" in {
        quoteReceiverMock.expectMessageType[RepositoryActor.AddQuote](7 seconds)
    }
}
