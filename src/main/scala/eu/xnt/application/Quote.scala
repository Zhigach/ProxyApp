package eu.xnt.application

import CandleModels.Candle
import eu.xnt.application.model.Ticker

import java.nio.ByteBuffer
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

object Quote {
    def fromByteBuffer(bytes: ByteBuffer): Quote = {
        val ts = bytes.getLong
        val tickerLength = bytes.getShort
        val ticker =
            String(
                (for (i <- 1 to tickerLength) yield bytes.get).toArray,
                "US-ASCII"
            )
        val price = bytes.getDouble
        val size = bytes.getInt
        new Quote(1, ts, tickerLength, Ticker(ticker), price, size)
    }
}

final case class Quote(len: Short, timestamp: Long, tickerLen: Short, ticker: Ticker, price: Double, size: Int) {
    override def toString: String =
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"))
        String(s"[$time]: $ticker P: $price, S: $size")
}
