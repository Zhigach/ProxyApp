package eu.xnt.application.repository.testutils

import akka.util.ByteString
import eu.xnt.application.model.Quote

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object Util {
    def randomQuote(timestamp: Long, ticker: String = "TEST", price: Int = 1): Quote = {
        Quote(
            timestamp = timestamp,
            tickerLen = ticker.length.toShort,
            ticker = ticker,
            price = price,
            size = 1
        )
    }

    def encodeQuote(q: Quote): Array[Byte] = {
        val buffer = ByteBuffer.allocate(q.len + 2)
          .order(java.nio.ByteOrder.BIG_ENDIAN)
          .putShort(q.len)
          .putLong(q.timestamp)
          .putShort(q.tickerLen)
          .put(q.ticker.getBytes(StandardCharsets.US_ASCII))
          .putDouble(q.price)
          .putInt(q.size)
          .array()
        buffer
    }
}
