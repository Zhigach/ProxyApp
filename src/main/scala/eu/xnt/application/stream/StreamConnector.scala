package eu.xnt.application.stream

import akka.actor.TypedActor.dispatcher
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.typesafe.scalalogging.LazyLogging
import eu.xnt.application.stream.StreamConnector.{Command, Connect, Connected, Failed}

import java.io.{IOException, InputStream}
import scala.concurrent.Future
import scala.util.{Failure, Success}

class StreamConnector(val connection: Connection, context: ActorContext[Command]) extends AbstractBehavior[Command] with LazyLogging {

    private def connect(): Future[InputStream] = {
        connection.getStream.map {
            inputStream =>
                inputStream
        }(context.executionContext) recoverWith {
            case exception =>
                Future.failed(new IOException("Stream connection failed", exception))
        }
    }


    override def onMessage(msg: Command): Behavior[Command] = {
        Behaviors.setup { context =>
            Behaviors.receiveMessage {
                case Connect(replyTo) =>
                    context.pipeToSelf(connect()) {
                        case Failure(exception) =>
                            logger.error(s"Exception occurred connecting ${connection.host}: ${connection.port}", exception)
                            replyTo ! StreamReader.WrappedFailed(Failed(exception))
                            Failed(exception)
                        case Success(inputStream) =>
                            logger.info(s"Stream connected at ${connection.host}: ${connection.port}")
                            replyTo ! StreamReader.WrappedConnected(Connected(inputStream))
                            Connected(inputStream)
                    }
                    Behaviors.same
                case _ =>
                    Behaviors.same
            }
        }
    }
}

object StreamConnector extends LazyLogging {

    sealed trait Command
    case class Connect(replyTo: ActorRef[StreamReader.Command]) extends Command
    case class Connected(inputStream: InputStream) extends Command
    case class Failed(exception: Throwable) extends Command


    def apply(connection: Connection): Behavior[Command] = {
        Behaviors.setup { context =>
            new StreamConnector(connection, context)
        }
    }



}
