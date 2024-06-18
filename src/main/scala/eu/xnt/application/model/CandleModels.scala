package eu.xnt.application.model

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.duration.*

object CandleModels {
        
    case class Candle (ticker: String,
                      timestamp: LocalDateTime,
                      duration: FiniteDuration = 1.minute,
                      open: Double,
                      high: Double,
                      low: Double,
                      close: Double,
                      volume: Int) {
        override def toString: String = {
            String(s"$timestamp: O: $open, C: $close, VOL: $volume")
        }
    }

    case class CandleRequest(ticker: String, limit: Int)

    case class CandleResponse(candles: Array[Candle])

    
    
    def updateCandle(quote: Quote, candle: Candle): Candle = {
        val price = quote.price
        val h = math.max(candle.high, price)
        val l = math.min(candle.low, price)
        val vol = candle.volume + quote.size
        candle.copy(high = h, low = l, volume = vol, close=quote.price)
    }

    def newCandleFromQuote(quote: Quote): Candle = {
        val quoteTS = LocalDateTime.ofInstant(Instant.ofEpochMilli(quote.timestamp), ZoneId.of("UTC"))
        Candle(
            ticker = quote.ticker,
            timestamp = quoteTS.truncatedTo(ChronoUnit.MINUTES),
            open = quote.price,
            high = quote.price,
            low = quote.price,
            close = quote.price,
            volume = quote.size
        )
    }

}
