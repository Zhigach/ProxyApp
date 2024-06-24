package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.{CandleModels, Quote}

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

class InMemoryCandleRepository(val candleDuration: Long) {

    private val candleBuffers: ArrayBuffer[CandleBuffer] = ArrayBuffer()

    def getHistoricalCandles(depth: Int = 1): Array[Candle] = {
        candleBuffers.flatMap(cb => cb.getHistoricalCandles(depth)).toArray
    }

    def getIterableBuffer(depth: Int): Array[Candle] =
        candleBuffers.flatMap(cb => cb.getCandles(depth)).toArray

    def addQuote(quote: Quote, buffer: CandleBuffer): Unit = {
        buffer.addQuote(quote)
    }

    def addQuote(q: Quote): Unit = {        
        val ticker = q.ticker
        val quoteTS = LocalDateTime.ofInstant(Instant.ofEpochMilli(q.timestamp), ZoneId.of("UTC"))
        val optBuffer = getBuffer(ticker)
        optBuffer match
            case Some(buffer) =>
                addQuote(q, buffer)
            case None => // если буфера для такого тикера нет, то создаем новый                
                candleBuffers.addOne(CandleBuffer(ticker, candleDuration))
                val newBuffer = getBuffer(ticker)
                addQuote(q, newBuffer.get)
        //TODO: remove debug
        //println("- - - - -"); candleBuffers.foreach(println(_)); println("- - - - -")

    }

    def getLastCandle(ticker: String, limit: Int): Array[Candle] =
        val buffer = getBuffer(ticker)
        buffer match
            case Some(buf) =>
                buf.getCandles(limit)
            case None =>
                Array[Candle]()

    private def getBuffer(ticker: String): Option[CandleBuffer] =
        val buffer = candleBuffers.find(b => b.ticker == ticker)        
        buffer
}
