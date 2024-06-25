package eu.xnt.application.stream

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy}
import akka.pattern.pipe
import Command.*
import eu.xnt.application.model.Quote
import eu.xnt.application.stream.StreamReader.{ReadStream, StreamData, StreamFailure}

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class StreamReader(connection: ConnectionAddress, quoteReceiver: ActorRef) extends Actor with ActorLogging {
    
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case _ : RuntimeException => SupervisorStrategy.Restart
    }

    override def preStart(): Unit = {
        self ! Connect(connection)
    }

    private def connected(inputStream: InputStream): Receive = {
        case ReadStream =>
            readStream(inputStream).pipeTo(self)
        case StreamData(byteBuffer) =>
            quoteReceiver ! Quote.fromByteBuffer(byteBuffer)
            self ! ReadStream
        case StreamFailure(exception) =>
            log.error(s"Exception occurred during connection: ${exception.getMessage}")
            Thread.sleep(5000)
            self ! Connect(connection)
    }

    private def readStream(inputStream: InputStream): Future[StreamData] = {
        try {
            val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
            val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
            Future.successful(StreamData(messageBuffer))
        } catch
            case exception: RuntimeException =>
                Future.failed(exception)
    }

    private def disconnected(): Receive = {
        case Connect(inputStream) =>
            connect().pipeTo(self)
    }

    override def postStop(): Unit = {
        log.info(s"${self.path.name} stopped")
    }

    def getCandleRepository: ActorRef = quoteReceiver
    
    def connect(): Future[InputStream] = {
        connection.getStream.map {
            inputStream =>
                log.info(s"Stream connected")
                context.become(connected(inputStream))
                self ! ReadStream
                inputStream
        } recoverWith {
            case exception =>
                log.error(s"Exception occurred during connection: ${exception.getMessage}")
                Thread.sleep(5000)
                self ! Connect(connection)                
                Future.failed(new RuntimeException("Reconnecting due to failure", exception))
        }
    }

    private def startReadingStream(inputStream: InputStream): Unit = {
        var streamIsActive = true

        while streamIsActive do
            try {
                val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
                val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
                quoteReceiver ! Quote.fromByteBuffer(messageBuffer)
            } catch
                case exception: RuntimeException =>
                    log.error(s"Exception occurred reading stream: ${exception.getMessage}")
                    streamIsActive = false
                    Thread.sleep(2000)
                    self ! Connect(connection)
        log.info("Finished reading stream")
    }

    override def receive: Receive = disconnected()

}

object StreamReader {
    def props(connection: ConnectionAddress, quoteReceiver: ActorRef): Props =
        Props(new StreamReader(connection, quoteReceiver))

    case class Connect(connection: ConnectionAddress)
    case object ReadStream
    case class StreamData(messageBuffer: ByteBuffer)
    case class StreamFailure(exception: Throwable)
}
