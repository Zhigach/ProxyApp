package eu.xnt.application.adapter

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.Quote

trait QuoteReceiverActor extends Actor with ActorLogging{
    
    override def receive: Receive =
        case Quote => log.info("Dummy receive")
}
