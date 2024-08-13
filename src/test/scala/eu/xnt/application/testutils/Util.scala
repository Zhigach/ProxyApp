package eu.xnt.application.testutils

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.util.ByteString
import eu.xnt.application.model.Quote
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.JsonParser

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

    def encodeQuoteWithLengthPrefix(q: Quote): Array[Byte] = {
        ByteBuffer.allocate(2).putShort(q.len).array() ++ encodeQuote(q)
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

    def handleHttpResponse(response: HttpResponse, testProbe: TestProbe[String])(implicit materializer: Materializer) = {
        response.entity.dataBytes.runForeach { chunk: ByteString =>
            val json = JsonParser(chunk.utf8String)
            val ticker = json.asJsObject.fields("ticker").convertTo[String]
            testProbe.ref ! ticker
        }(materializer)
    }
}
