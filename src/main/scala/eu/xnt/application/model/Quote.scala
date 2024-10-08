package eu.xnt.application.model


import java.nio.{BufferUnderflowException, ByteBuffer}
import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.util.{Failure, Success, Try}

object Quote {
    /**
     * Parse `ByteBuffer` into a quote by the following scheme
     * [ LEN:2 ] [ TIMESTAMP:8 ] [ TICKER_LEN:2 ] [ TICKER:TICKER_LEN ] [ PRICE:8 ] [ SIZE:4 ]
     * <li>LEN: длина последующего сообщения (целое, 2 байта) *not included into quote bytes - part of a stream</li>
     * <li>TIMESTAMP: дата и время события (целое, 8 байт, milliseconds since epoch)</li>
     * <li>TICKER_LEN: длина биржевого тикера (целое, 2 байта)</li>
     * <li>TICKER: биржевой тикер (ASCII, TICKER_LEN байт)</li>
     * <li>PRICE: цена сделки (double, 8 байт)</li>
     * <li>SIZE: объем сделки (целое, 4 байта)</li>
     */
    def parse(bytes: ByteBuffer): Try[Quote] = {
        try {
            val ts = bytes.getLong
            val tickerLength = bytes.getShort
            val ticker = new String((1 to tickerLength).map(_ => bytes.get).toArray, "US-ASCII")
            val price = bytes.getDouble
            val size = bytes.getInt
            Success(new Quote(ts, tickerLength, ticker, price, size))
        } catch {
            case e: BufferUnderflowException =>
                Failure(e)
        }
    }
}

final case class Quote(timestamp: Long, tickerLen: Short, ticker: String, price: Double, size: Int) extends JsonSupport {
    def len: Short = (8 /*timestamp*/ + 2 /*ticker len*/ + tickerLen/*ticker bytes itself*/ + 8 /*price bytes*/ + 4/*size bytes*/).toShort

    override def toString: String = {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"))
        String.format(s"[$time]: $ticker P: $price, S: $size")
    }
}
