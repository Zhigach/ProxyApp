package eu.xnt.application.stream

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import Command.*
import eu.xnt.application.Quote
import eu.xnt.application.adapter.QuoteProcessor

import java.io.InputStream
import java.nio.ByteBuffer
import scala.language.postfixOps
import scala.util.{Failure, Success}

class StreamHandler(connection: ConnectionAddress) extends Actor with ActorLogging {

    private val quoteProcessor: ActorRef = context.actorOf(Props[QuoteProcessor](), "QuoteProcessor")
    
    def connect(): Unit = {
        val inputStream = connection.getStream
        inputStream match
            case Success(value) =>
                log.info(s"Stream connected")
                startReadingStream(value)
            case Failure(exception) =>
                log.error(s"Exception occurred during connection: ${exception.getMessage}")
                Thread.sleep(5000)
                self ! Connect(connection)
    }

    private def startReadingStream(inputStream: InputStream): Unit = {        
        var streamIsActive = true

        while streamIsActive do
            try {
                val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
                val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))                
                quoteProcessor ! Quote.fromByteBuffer(messageBuffer)           
            } catch
                case exception: RuntimeException =>
                    log.error(s"Exception occurred reading stream: ${exception.getMessage}")
                    streamIsActive = false
                    Thread.sleep(2000)
                    self ! Connect(connection)
        log.info("Finished reading stream")
    }

    override def receive: Receive = {
        case Connect(connection) =>
            log.info("Connecting to stream at {}:{}", connection.host, connection.port);
            connect()
        case Disconnect(connection) =>
            log.info("Disconnecting...") //FIXME no real disconnection
        case _ => log.error("Unknown message received")
    }

}
