package eu.xnt.application.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.concurrent.duration.*

object CandleModels {

    trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {        
        implicit val candleFormat: RootJsonFormat[Candle] = jsonFormat8(Candle.apply)
    }

    case class Candle (ticker: String,
                      timestamp: Long,
                      duration: Long = 60000, //time span in millis
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
