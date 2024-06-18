package eu.xnt.application.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.Collections
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.{Instant, LocalDateTime, ZoneId}
import spray.json.*

import scala.collection.immutable.{AbstractSeq, LinearSeq}

object CandleModels {

    trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
        implicit object CandleJsonFormat extends RootJsonFormat[Candle] {
            def write(c: Candle): JsObject =
                JsObject(
                    "ticker" -> JsString(c.ticker),
                    "timestamp" -> JsNumber(c.timestamp),
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
                    case _ => throw new DeserializationException("Candle JSON expected")
            }
        }
            //jsonFormat[String, Long, Long, Double, Double, Double, Double, Int, Candle]
        // (Candle.apply, "ticker", "timestamp", "duration", "open", "close", "high", "low", "volume")

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
            //String(s"$timestamp: O: $open, C: $close, VOL: $volume")
            import spray.json.enrichAny
            this.toJson.toString
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
        try {
            Candle(
                ticker = quote.ticker,
                timestamp = (quote.timestamp / 60000) * 60000,
                open = quote.price,
                high = quote.price,
                low = quote.price,
                close = quote.price,
                volume = quote.size
            )
        } catch
            case e: Exception => println(e.getMessage);
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
