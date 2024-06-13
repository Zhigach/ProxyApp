package eu.xnt.application.stream

import java.io.{IOException, InputStream}
import java.net.{InetSocketAddress, Socket}
import scala.util.{Failure, Success, Try}

final case class ConnectionAddress(host: String, port: Int) {
    def getStream: Try[InputStream] = {
        val socket = new Socket
        val endpoint = new InetSocketAddress(host, port)
        try {
            socket.connect(endpoint)
            val in = socket.getInputStream
            Success(in)
        } catch
            case ex: IOException =>
                Failure(ex)
    }
}
