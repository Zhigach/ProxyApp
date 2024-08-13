package eu.xnt.application.stream

import java.io.{IOException, InputStream}
import java.net.{InetSocketAddress, Socket}
import scala.concurrent.Future

final case class Connection(host: String, port: Int) {

    def getStream: Future[InputStream] = {
        val socket = new Socket
        val endpoint = new InetSocketAddress(host, port)
        try {
            socket.connect(endpoint)
            val in = socket.getInputStream
            Future.successful(in)
        } catch {
            case ex: IOException =>

                Future.failed(ex)
        }
    }

}
