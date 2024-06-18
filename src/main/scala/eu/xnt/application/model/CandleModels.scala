package eu.xnt.application.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZonedDateTime}

object CandleModels {

    trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
        implicit object CandleJsonFormat extends RootJsonFormat[Candle] {
            def write(c: Candle): JsObject =
                JsObject(
                    "ticker" -> JsString(c.ticker),
                    "timestamp" -> JsString(
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(c.timestamp), ZoneId.of("UTC"))
                            .format(DateTimeFormatter.ISO_INSTANT)
                    ),
                    "open" -> JsNumber(c.open),
                    "high" -> JsNumber(c.high),
                    "low" -> JsNumber(c.low),
                    "close" -> JsNumber(c.close),
                    "volume" -> JsNumber(c.volume)
                )

            def read(value: JsValue): Candle = {
                value.asJsObject.getFields("ticker", "timestamp", "open", "high", "low", "close", "volume") match
                    case Seq(JsString(ticker), JsNumber(timestamp), JsNumber(open),
                    JsNumber(high), JsNumber(low), JsNumber(close), JsNumber(volume)) =>
                        new Candle(ticker = ticker,
                            timestamp = timestamp.toLong,
                            open = open.toDouble,
                            high = high.toDouble,
                            low = low.toDouble,
                            close = close.toDouble,
                            volume = volume.toInt)
                    case _ => throw DeserializationException("Candle JSON expected")
            }
        }
    }

    case class Candle (ticker: String,
                      timestamp: Long,
                      duration: Long = 60000, //time span in millis
                      open: Double,
                      high: Double,
                      low: Double,
                      close: Double,
                      volume: Int) extends JsonSupport {
        override def toString: String = {
            this.toJson.compactPrint
        }
    }

    case class CandleRequest(ticker: String, limit: Int)

    case class CandleResponse(candles: Array[Candle]) extends JsonSupport {
        override def toString: String = {
            val jsArray = (for (c <- candles) yield c.toJson).toVector
            JsArray(jsArray).compactPrint
        }
    }



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
