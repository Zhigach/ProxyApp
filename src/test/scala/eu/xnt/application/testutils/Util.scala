package eu.xnt.application.testutils

import eu.xnt.application.model.Quote

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object Util {
    def randomQuote(timestamp: Long = System.currentTimeMillis(), ticker: String = "TEST", price: Double = 111.11): Quote = {
        Quote(
            timestamp = timestamp,
            tickerLen = ticker.length.toShort,
            ticker = ticker,
            price = price,
            size = 1
        )
    }

    def oldQuote(ageInMinutes: Int, ticker: String = "TEST"): Quote = {
        randomQuote((System.currentTimeMillis() / 60000 - ageInMinutes) * 60000 + 1, ticker)
    }

    def encodeQuote(q: Quote): Array[Byte] = {
        val buffer = ByteBuffer.allocate(q.len)
          .order(java.nio.ByteOrder.BIG_ENDIAN)
          .putLong(q.timestamp)
          .putShort(q.tickerLen)
          .put(q.ticker.getBytes(StandardCharsets.US_ASCII))
          .putDouble(q.price)
          .putInt(q.size)
          .array()
        buffer
    }
}
