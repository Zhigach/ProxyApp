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
        new Quote(1, ts, tickerLength, ticker, price, size)
    }
}

final case class Quote(len: Short, timestamp: Long, tickerLen: Short, ticker: String, price: Double, size: Int) {
    override def toString: String = {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"))
        String.format(s"[$time]: $ticker P: $price, S: $size")
    }
}
