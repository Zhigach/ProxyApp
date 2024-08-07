package eu.xnt.application.stream

import akka.actor.TypedActor.context
import akka.actor.typed.{ActorRef, Behavior, PreRestart, Signal, Terminated}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{AddQuote, RepositoryCommand}
import eu.xnt.application.stream.StreamReader.{Command, ReadStream, StreamData, reconnectPeriod}

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
    case class WrappedConnectorResponse(status: StreamConnector.ConnectionStatus) extends Command

    val reconnectPeriod: FiniteDuration = 5 seconds

    def apply(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand]): Behavior[Command] = {
        Behaviors.setup { context =>
            val streamConnector = context.spawn(StreamConnector(connection), "StreamConnector")
            val streamReader = new StreamReader(streamConnector, quoteReceiver, context)
            streamReader
        }
    }
}

class StreamReader(streamConnector: ActorRef[StreamConnector.Command], quoteReceiver: ActorRef[RepositoryCommand], context: ActorContext[Command])
  extends AbstractBehavior[Command] with LazyLogging {

    /*override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case _ : RuntimeException => SupervisorStrategy.Restart
    }*/ //FIXME do that for the akka.typed

    /**
     * Disconnected state
     * @param msg
     * @return
     */
    override def onMessage(msg: Command): Behavior[Command] = {
        msg match {
            case StreamReader.ReadStream(inputSteam) =>
                logger.trace("ReadStream while connected")
                context.pipeToSelf(readStream(inputSteam)) {
                    case Failure(exception) =>
                        logger.error("Error during reading stream: {}", exception)
                        StreamReader.ReadStream(inputSteam)
                    case Success(streamData) =>
                        streamData
                }
                context.self ! StreamReader.ReadStream(inputSteam)
                Behaviors.same
            case StreamReader.StreamData(messageBuffer) =>
                logger.trace("StreamData received")
                val quote = Quote.parse(messageBuffer)
                quoteReceiver ! AddQuote(quote)
                Behaviors.same
            case StreamReader.WrappedConnectorResponse(status) =>
                logger.trace("ConnectionStatus message received")
                status match {
                    case StreamConnector.Connected(inputStream) =>
                        context.self ! StreamReader.ReadStream(inputStream)
                        Behaviors.same
                    case StreamConnector.Failed(_) =>
                        context.scheduleOnce(reconnectPeriod, streamConnector, StreamConnector.Connect(context.self))
                        Behaviors.same
                }
            case _ =>
                logger.info("Unsupported message received")
                Behaviors.same
        }
    }


    private def readStream(inputStream: InputStream): Future[StreamData] = {
        try {
            val msgLength: Short = ByteBuffer.wrap(inputStream.readNBytes(2)).getShort
            val messageBuffer: ByteBuffer = ByteBuffer.wrap(inputStream.readNBytes(msgLength))
            Future.successful(StreamData(messageBuffer))
        } catch {
            case exception: RuntimeException =>
                logger.error(s"${exception.getMessage}")
                Future.failed(new RuntimeException("Stream failed", exception))
        }
    }


    override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
        case Terminated(value) => logger.info(s"${context.self.path.name} stopped by ${value.path.name}"); Behaviors.stopped
        case restart: PreRestart => streamConnector ! StreamConnector.Connect(context.self); this
    }

    Thread.sleep(1000)
    logger.debug("I'am {}", context.self)
    streamConnector ! StreamConnector.Connect(context.self)
    logger.debug("After message is sent")
}
