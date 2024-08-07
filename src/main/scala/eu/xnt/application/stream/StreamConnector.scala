package eu.xnt.application.stream

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.stream.StreamConnector.{Command, Connect, Connected, Failed}

import java.io.{IOException, InputStream}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}


object StreamConnector extends LazyLogging {

    sealed trait Command
    case class Connect(replyTo: ActorRef[StreamReader.Command]) extends Command

    sealed trait ConnectionStatus
    case class Connected(inputStream: InputStream) extends ConnectionStatus
    case class Failed(exception: Throwable) extends ConnectionStatus


    def apply(connection: Connection): Behavior[Command] = {
        Behaviors.setup { context =>
            new StreamConnector(connection, context).basicBehavior()
        }
    }
}

class StreamConnector(val connection: Connection, context: ActorContext[Command]) extends LazyLogging {

    def basicBehavior(): Behavior[Command] = {
        implicit val ec: ExecutionContextExecutor = context.executionContext
        Behaviors.receiveMessage {
            case Connect(replyTo) =>

                def connect(): Future[InputStream] = {
                    connection.getStream.map {
                        inputStream =>
                            inputStream
                    } recoverWith {
                        case exception =>
                            Future.failed(new IOException("Stream connection failed", exception))
                    }
                }

                logger.trace("Connect message received")
                connect() onComplete {
                    case Failure(exception) =>
                        logger.error(s"Exception occurred connecting ${connection.host}: ${connection.port}", exception)
                        replyTo ! StreamReader.WrappedConnectorResponse(Failed(exception))
                    case Success(inputStream) =>
                        logger.info(s"Stream connected at ${connection.host}: ${connection.port}")
                        replyTo ! StreamReader.WrappedConnectorResponse(Connected(inputStream))
                }
                Behaviors.same
            case _ =>
                logger.info("Unsupported message received")
                Behaviors.same
        }
    }
}
