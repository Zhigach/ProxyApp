package eu.xnt.application.model

import java.nio.ByteBuffer
import java.time.{Instant, ZoneId, ZonedDateTime}

object Quote {
    def parse(bytes: ByteBuffer): Quote = {
        val ts = bytes.getLong
        val tickerLength = bytes.getShort
        val ticker = new String((for (i <- 1 to tickerLength) yield bytes.get).toArray, "US-ASCII")
        val price = bytes.getDouble
        val size = bytes.getInt
        new Quote(ts, tickerLength, ticker, price, size)
    }
}

final case class Quote(timestamp: Long, tickerLen: Short, ticker: String, price: Double, size: Int) extends JsonSupport {
    def len: Short = (8 + 2 + tickerLen + 8 + 4).toShort

    override def toString: String = {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"))
        String.format(s"[$time]: $ticker P: $price, S: $size")
    }
}
