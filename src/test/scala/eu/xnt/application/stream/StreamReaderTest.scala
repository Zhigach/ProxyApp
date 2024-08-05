package eu.xnt.application.stream

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.testkit.{TestKit, TestProbe}
import akka.util.ByteString
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.read.ListAppender
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.testutils.Util
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps


class StreamReaderTest extends TestKit(ActorSystem("StreamReaderTest"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    private val quoteSource = Source(1 to 4).throttle(1, 1 second)
      .map(_ => Util.randomQuote(System.currentTimeMillis()))
      .map(q => ByteString(Util.encodeQuote(q)))

    val quoteFlow: Flow[ByteString, ByteString, NotUsed] = Flow.fromSinkAndSource(Sink.ignore, quoteSource)

    val connection: Connection = Connection("localhost", 5554)

    Tcp()
      .bind("localhost", 5554)
      .runForeach {
          connection => connection.handleWith(quoteFlow)
      }

    val quoteReceiverMock = TestProbe()
    val logProbe = TestProbe()
    system.eventStream.subscribe(logProbe.ref, classOf[Logging.LogEvent])
    /*val logger: Logger = LoggerFactory.getLogger(classOf[StreamReader]).asInstanceOf[Logger]
    val listAppender: Appender[ILoggingEvent] = new ListAppender[ILoggingEvent]
    listAppender.start()
    logger
      .addAppender(listAppender)*/

    val streamReader = system.actorOf(Props(new StreamReader(connection, quoteReceiverMock.ref)), "StreamReader")

    "StreamReader" must {

        "connects and starts receiving quotes" in {
            quoteReceiverMock.expectMsgClass(10 seconds, classOf[Quote])
        }

        "stream fails" in {
            logProbe.fishForMessage(10 seconds) {
                case str: String if str.contains("Stream failed") => true
                case Logging.Error(_, _, _, msg: String) /*if msg.contains("Stream failed")*/ => println(msg); true
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
        TestKit.shutdownActorSystem(system)
    }
}
