package eu.xnt.application.model

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import eu.xnt.application.model.CandleModels.Candle
import spray.json.{DefaultJsonProtocol, DeserializationException, JsNumber, JsObject, JsString, JsValue, JsonReader, JsonWriter, RootJsonFormat}

import java.time.{Instant, ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol 

object JsonSupport {
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
            value.asJsObject.getFields("ticker", "timestamp", "open", "high", "low", "close", "volume") match {
                case Seq(JsString(ticker), JsNumber(timestamp), JsNumber(open),
                JsNumber(high), JsNumber(low), JsNumber(close), JsNumber(volume)) =>
                    Candle(ticker = ticker,
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
}
