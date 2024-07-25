package eu.xnt.application.stream

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Status, SupervisorStrategy}
import akka.pattern.pipe
import eu.xnt.application.model.Quote
import eu.xnt.application.stream.StreamReader.{Connect, ReadStream, StreamData, reconnectPeriod}

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class StreamReader(connection: ConnectionAddress, quoteReceiver: ActorRef) extends Actor with ActorLogging {
    
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case _ : RuntimeException => SupervisorStrategy.Restart
    }

    override def receive: Receive = disconnected()

    override def preStart(): Unit = {
        self ! Connect(connection)
    }

    private def disconnected(): Receive = {
        case Connect(connection) =>
            log.info("Connecting to {}: {}", connection.host, connection.port)
            connect().pipeTo(self)
        case Status.Failure(exception) =>
            log.info(s"${exception.getMessage}")

    }

    private def connected(inputStream: InputStream): Receive = {
        case ReadStream =>
            readStream(inputStream).pipeTo(self)
        case StreamData(byteBuffer) =>
            quoteReceiver ! Quote.parse(byteBuffer)
            self ! ReadStream
        case Status.Failure(exception) =>
            log.error(s"${exception.getMessage}")
            context.become(disconnected())
            Thread.sleep(reconnectPeriod.toMillis)
            self ! Connect(connection)
        case inputStream: InputStream =>
            self ! ReadStream
    }

    private def readStream(inputStream: InputStream): Future[StreamData] = {
        try {
            val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
            val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
            Future.successful(StreamData(messageBuffer))
        } catch {
            case exception: RuntimeException =>
                Future.failed(new RuntimeException("Stream failed", exception))
        }
    }
    
    def connect(): Future[InputStream] = {
        connection.getStream.map {
            inputStream =>
                log.info(s"Stream connected at ${connection.host}: ${connection.port}")
                context.become(connected(inputStream))
                inputStream
        } recoverWith {
            case exception =>
                log.error(s"Exception occurred connecting ${connection.host}: ${connection.port}\n ${exception.getMessage}")
                context.become(disconnected())
                Thread.sleep(reconnectPeriod.toMillis)
                self ! Connect(connection)
                Future.failed(new RuntimeException("Reconnecting due to failure", exception))
        }
    }

    override def postStop(): Unit = {
        log.info(s"${self.path.name} stopped")
    }

}

object StreamReader {
    case class Connect(connection: ConnectionAddress)
    case object ReadStream
    case class StreamData(messageBuffer: ByteBuffer)

    val reconnectPeriod: FiniteDuration = 5 seconds
}
