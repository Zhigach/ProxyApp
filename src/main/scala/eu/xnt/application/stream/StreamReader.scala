package eu.xnt.application.stream

import akka.actor.TypedActor.self
import akka.actor.typed.{ActorRef, Behavior, PreRestart, Signal, Terminated}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.model.Quote
import eu.xnt.application.repository.RepositoryActor.{AddQuote, RepositoryCommand}
import eu.xnt.application.stream.StreamReader.{Command, Connect, ConnectedStateCommands, DisconnectedStateCommands, ReadStream, StreamData, reconnectPeriod}

import java.io.InputStream
import java.nio.ByteBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class StreamReader(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand], context: ActorContext[Command])
  extends AbstractBehavior[Command] with LazyLogging {
    
    implicit val ec: ExecutionContext = context.executionContext //FIXME глобальный и акторный ec - зло

    /*override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
        case _ : RuntimeException => SupervisorStrategy.Restart
    }*/ //FIXME do that for the akka.typed

    logger.info("Stream reader created")

    override def onMessage(msg: Command): Behavior[Command] = {
        Behaviors.receive { (context, message) =>
            message match {
                case Connect(connection) =>
                    logger.info("Connecting to {}: {}", connection.host, connection.port)
                    val connectFuture = connect()
                    //Await.ready(connectFuture, 1 second)
                    connectFuture onComplete {
                        case Failure(exception) =>
                            logger.error(s"Exception occurred connecting ${connection.host}: ${connection.port}", exception)
                            Thread.sleep(reconnectPeriod.toMillis) // FIXME is it a good idea to use Thread.sleep?
                            context.self ! Connect(connection)
                        case Success(stream) =>
                            logger.info(s"Stream connected at ${connection.host}: ${connection.port}")
                            //context.self ! StreamReader.ReadStream
                            connected(stream)
                    }
                    Behaviors.same
            }
        }
    }

    /*private def disconnected(): Behavior[DisconnectedStateCommands] = {
        Behaviors.receive[DisconnectedStateCommands] { (context, message) =>
            message match {
                case Connect(connection) =>
                    logger.info("Connecting to {}: {}", connection.host, connection.port)
                    val connectFuture = connect()
                    //Await.ready(connectFuture, 1 second)
                    connectFuture onComplete {
                        case Failure(exception) =>
                            logger.error(s"Exception occurred connecting ${connection.host}: ${connection.port}", exception)
                            Thread.sleep(reconnectPeriod.toMillis) // FIXME is it a good idea to use Thread.sleep?
                            context.self ! Connect(connection)
                        case Success(stream) =>
                            logger.info(s"Stream connected at ${connection.host}: ${connection.port}")
                            //context.self ! StreamReader.ReadStream
                            connected(stream)
                    }
                    Behaviors.same

                /*case StreamReader.ReadStream =>
                    logger.error("Unsupported type ReadStream")
                    Behaviors.same

                case StreamData(_)=>
                    logger.error("Unsupported type ReadStream")
                    Behaviors.same*/
            }
        }

    }*/

    private def connected(inputStream: InputStream): Behavior[ConnectedStateCommands] = {
        context.self ! ReadStream

        Behaviors.receive[ConnectedStateCommands] { (context, message) =>
            message match {
                /*case Connect(_) =>
                logger.error("Unsupported type Connect")
                Behaviors.same*/
                case StreamReader.ReadStream =>
                    readStream(inputStream) onComplete {
                        case Success(streamData) =>
                            context.self ! streamData
                    }
                    Behaviors.same

                case StreamData(messageBuffer) =>
                    val quote = Quote.parse(messageBuffer)
                    quoteReceiver ! AddQuote(quote)
                    context.self ! ReadStream
                    Behaviors.same
            }
        }


        /*case ReadStream =>
            readStream(inputStream).pipeTo(self)
        case StreamData(byteBuffer) =>
            quoteReceiver ! Quote.parse(byteBuffer)
            self ! ReadStream
        case Status.Failure(exception) =>
            logger.error(s"${exception.getMessage}")
            context.become(disconnected())
            Thread.sleep(reconnectPeriod.toMillis)
            self ! Connect(connection)*/
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
    
    private def connect(): Future[InputStream] = {
        connection.getStream.map {
            inputStream =>
                //logger.info(s"Stream connected at ${connection.host}: ${connection.port}")
                //context.become(connected(inputStream))
                //context.self ! StreamReader.ReadStream
                inputStream
        } recoverWith {
            case exception =>
                //logger.error(s"Exception occurred connecting ${connection.host}: ${connection.port}", exception)
                //context.become(disconnected())
                //Thread.sleep(reconnectPeriod.toMillis)
                //self ! Connect(connection)
                Future.failed(new RuntimeException("Stream connection failed", exception))
        }
    }


    override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
        case Terminated(value) => logger.info(s"${context.self.path.name} stopped by ${value.path.name}"); Behaviors.stopped
        case restart: PreRestart => context.self ! Connect(connection); this
    }

    context.self ! Connect(connection)

}

object StreamReader {

    sealed trait Command

    private sealed trait ConnectedStateCommands extends Command
    private case object ReadStream extends ConnectedStateCommands
    private case class StreamData(messageBuffer: ByteBuffer) extends ConnectedStateCommands

    sealed trait DisconnectedStateCommands extends Command
    case class Connect(connection: Connection) extends DisconnectedStateCommands


    /*private sealed trait Command
    private case class Connect(connection: ConnectionAddress) extends Command
    private case object ReadStream extends Command
    private case class StreamData(messageBuffer: ByteBuffer) extends Command*/

    val reconnectPeriod: FiniteDuration = 5 seconds

    def apply(connection: Connection, quoteReceiver: ActorRef[RepositoryCommand]): Behavior[Command] = {
        Behaviors.setup { context: ActorContext[Command] =>
           new StreamReader(connection, quoteReceiver, context)
        }
    }

}
