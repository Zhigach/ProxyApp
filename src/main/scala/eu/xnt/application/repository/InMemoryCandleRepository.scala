package eu.xnt.application.repository

import akka.actor.{Actor, ActorLogging}
import eu.xnt.application.CandleModels.{Candle, CandleRequest}
import eu.xnt.application.CandleModels.Candle
import eu.xnt.application.{CandleModels, Quote}
import eu.xnt.application.model.Ticker

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*
import scala.language.postfixOps

class InMemoryCandleRepository {

    private val candleBuffers: ArrayBuffer[CandleBuffer] = ArrayBuffer()

    def addQuote(q: Quote): Unit = {        
        val ticker = q.ticker
        val quoteTS = LocalDateTime.ofInstant(Instant.ofEpochMilli(q.timestamp), ZoneId.of("UTC"))
        val buffer = getBuffer(ticker)
        buffer match
            case Some(buf) =>
                buf.addQuote(q)
            case None => // если буфера для такого тикера нет, то создаем новый
                candleBuffers.addOne(CandleBuffer(ticker))
                val newBuffer = getBuffer(ticker)
                newBuffer.get.addQuote(q)

        //TODO: remove debug
        println("- - - - -"); candleBuffers.foreach(println(_)); println("- - - - -")

    }

    def getLastCandle(ticker: Ticker, limit: Int): Array[Candle] =
        val buffer = getBuffer(ticker)
        buffer match
            case Some(buf) =>
                buf.getCandles(limit)
            case None =>
                Array[Candle]()

    private def getBuffer(ticker: Ticker): Option[CandleBuffer] = {
        val buffer = candleBuffers.find(b => b.ticker == ticker)
        buffer
    }

}
