package eu.xnt.application.stream

object Command {
    final case class Connect(connection: ConnectionAddress)
    final case class Disconnect(connection: ConnectionAddress)
}
