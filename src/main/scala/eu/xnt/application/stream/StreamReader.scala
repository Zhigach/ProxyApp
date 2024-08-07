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
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class StreamReader(streamConnector: ActorRef[StreamConnector.Command], quoteReceiver: ActorRef[RepositoryCommand], context: ActorContext[Command])
  extends AbstractBehavior[Command] with LazyLogging {

    //implicit val ec: ExecutionContext = context.dispatcher //FIXME глобальный и акторный ec - зло

    /*override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case _ : RuntimeException => SupervisorStrategy.Restart
    }*/ //FIXME do that for the akka.typed

    streamConnector ! StreamConnector.Connect(context.self)

    /**
     * Disconnected state
     * @param msg
     * @return
     */
    override def onMessage(msg: Command): Behavior[Command] = {
        Behaviors.setup { context =>
            Behaviors.receiveMessage[Command] {
                case StreamReader.ReadStream(inputSteam) =>
                    logger.debug("ReadStream while connected")
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
                    logger.debug("StreamData received")
                    val quote = Quote.parse(messageBuffer)
                    quoteReceiver ! AddQuote(quote)
                    Behaviors.same
                case StreamReader.WrappedConnected(connected) =>
                    context.self ! StreamReader.ReadStream(connected.inputStream)
                    Behaviors.same
                case StreamReader.WrappedFailed(failed) =>
                    context.scheduleOnce(reconnectPeriod, streamConnector, StreamConnector.Connect(context.self))
                    Behaviors.same

            }
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

    //context.self ! Connect(connection)

}

object StreamReader extends LazyLogging {

    sealed trait Command
    private case class ReadStream(inputSteam: InputStream) extends Command
    private case class StreamData(messageBuffer: ByteBuffer) extends Command
    case class WrappedConnected(connected: StreamConnector.Connected) extends Command
    case class WrappedFailed(failed: StreamConnector.Failed) extends Command

    val reconnectPeriod: FiniteDuration = 5 seconds

    def apply(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand]): Behavior[Command] = {
         Behaviors.setup { context =>
             val streamConnector = context.spawn(StreamConnector(connection), "StreamConnector")
             val streamReader = new StreamReader(streamConnector, quoteReceiver, context)
             streamReader
         }
    }

}
