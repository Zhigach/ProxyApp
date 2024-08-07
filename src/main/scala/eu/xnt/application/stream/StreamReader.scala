package eu.xnt.application.stream

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{AddQuote, RepositoryCommand}
import eu.xnt.application.stream.StreamReader.{Command, Reconnect, reconnectPeriod}

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object StreamReader extends LazyLogging {

    sealed trait Command
    private case class ReadStream(inputSteam: InputStream) extends Command
    private case object Reconnect extends Command
    case class WrappedConnectorResponse(status: StreamConnector.ConnectionStatus) extends Command


    private val reconnectPeriod: FiniteDuration = 5 seconds

    def apply(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand]): Behavior[Command] = {
        Behaviors.setup { context =>
            val streamConnector = context.spawn(StreamConnector(connection), "StreamConnector")
            val streamReader = new StreamReader(streamConnector, quoteReceiver, context)
            streamReader.disconnectedBehavior()
        }
    }
}

class StreamReader(streamConnector: ActorRef[StreamConnector.Command],
                   quoteReceiver: ActorRef[RepositoryCommand], context: ActorContext[Command])
  extends LazyLogging {

    private def disconnectedBehavior(): Behavior[Command] = {
        Behaviors.receiveMessage {
            case StreamReader.WrappedConnectorResponse(status) =>
                logger.trace("ConnectionStatus message received")
                status match {
                    case StreamConnector.Connected(inputStream) =>
                        context.self ! StreamReader.ReadStream(inputStream)
                        connectedBehavior()
                    case StreamConnector.Failed(_) =>
                        context.scheduleOnce(reconnectPeriod, streamConnector, StreamConnector.Connect(context.self))
                        Behaviors.same
                }
            case _ =>
                logger.info("Unsupported message received")
                Behaviors.same
        }
    }

    private def connectedBehavior(): Behavior[Command] = {
        implicit val ec: ExecutionContextExecutor = context.executionContext
            Behaviors.receiveMessage {
                case StreamReader.ReadStream(inputSteam) =>
                    readStream(inputSteam) onComplete {
                        case Failure(_) =>
                            context.self ! Reconnect
                        case Success(streamData) =>
                            logger.trace("StreamData received")
                            val quote = Quote.parse(streamData)
                            quoteReceiver ! AddQuote(quote)
                            context.self ! StreamReader.ReadStream(inputSteam)
                    }
                    Behaviors.same
                case StreamReader.Reconnect =>
                    streamConnector ! StreamConnector.Connect(context.self)
                    disconnectedBehavior()
                case _ =>
                    logger.info("Unsupported message received")
                    Behaviors.same
            }
    }


    private def readStream(inputStream: InputStream): Future[ByteBuffer] = {
        try {
            val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
            val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
            Future.successful(messageBuffer)
        } catch {
            case exception: RuntimeException =>
                logger.error("Failed reading stream", exception)
                Future.failed(new RuntimeException("Stream failed", exception))
        }
    }

    streamConnector ! StreamConnector.Connect(context.self)
}
