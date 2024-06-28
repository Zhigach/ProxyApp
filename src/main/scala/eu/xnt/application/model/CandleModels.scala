package eu.xnt.application.model

import spray.json._

object CandleModels {

    case class Candle(ticker: String,
                      timestamp: Long,
                      duration: Long = 60000,
                      open: Double,
                      high: Double,
                      low: Double,
                      close: Double,
                      volume: Int) extends JsonSupport
    
    case class HistoryRequest(limit: Int = 1)

    case class CandleResponse(candles: Array[Candle]) extends JsonSupport



    def updateCandle(quote: Quote, candle: Candle): Candle = {
        val price = quote.price
        val h = math.max(candle.high, price)
        val l = math.min(candle.low, price)
        val vol = candle.volume + quote.size
        candle.copy(high = h, low = l, volume = vol, close=quote.price)
    }

    def newCandleFromQuote(quote: Quote): Candle = {
        Candle(
            ticker = quote.ticker,
            timestamp = (quote.timestamp / 60000) * 60000,
            open = quote.price,
            high = quote.price,
            low = quote.price,
            close = quote.price,
            volume = quote.size
        )
    }

}
