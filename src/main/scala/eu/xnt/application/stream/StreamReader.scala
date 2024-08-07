package eu.xnt.application.stream

import akka.actor.OneForOneStrategy
import akka.actor.typed.{ActorRef, Behavior, PreRestart, Signal, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{AddQuote, RepositoryCommand}
import eu.xnt.application.stream.StreamReader.{AnotherCommand, Command, Reconnect, StreamData, reconnectPeriod}

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object StreamReader extends LazyLogging {

    sealed trait Command
    private case class ReadStream(inputSteam: InputStream) extends Command
    private case class StreamData(messageBuffer: ByteBuffer) extends Command
    private case object Reconnect extends Command
    case class WrappedConnectorResponse(status: StreamConnector.ConnectionStatus) extends Command

    sealed trait AnotherCommand
    final case class Cmd() extends AnotherCommand

    val reconnectPeriod: FiniteDuration = 5 seconds

    def apply(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand]): Behavior[Command] = {
        Behaviors.setup { context =>
            val streamConnector = context.spawn(StreamConnector(connection), "StreamConnector")
            val streamReader = new StreamReader(streamConnector, quoteReceiver, context)
            /*Behaviors.supervise(streamReader).onFailure {
                case _ => SupervisorStrategy.restart
            }*/
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
        implicit val ec = context.executionContext
            Behaviors.receiveMessage {
                case StreamReader.ReadStream(inputSteam) =>
                    readStream(inputSteam) onComplete {
                        case Failure(exception) =>
                            context.self ! Reconnect
                        case Success(streamData) =>
                            context.self ! streamData
                            context.self ! StreamReader.ReadStream(inputSteam)
                    }
                    Behaviors.same
                case StreamData(messageBuffer) =>
                    logger.trace("StreamData received")
                    val quote = Quote.parse(messageBuffer)
                    quoteReceiver ! AddQuote(quote)
                    Behaviors.same
                case StreamReader.Reconnect =>
                    streamConnector ! StreamConnector.Connect(context.self)
                    disconnectedBehavior()
                case _ =>
                    logger.info("Unsupported message received")
                    Behaviors.same
            }
    }

    def testBehavior(): Behavior[AnotherCommand] = {
        Behaviors.receiveMessage {
            case StreamReader.Cmd() => Behaviors.same
        }
    }

    private def readStream(inputStream: InputStream): Future[StreamData] = {
        try {
            val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
            val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
            Future.successful(StreamData(messageBuffer))
        } catch {
            case exception: RuntimeException =>
                logger.error("Failed reading stream", exception)
                Future.failed(new RuntimeException("Stream failed", exception))
        }
    }

    streamConnector ! StreamConnector.Connect(context.self)
}
