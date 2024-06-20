package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.{CandleModels, Quote}

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

class InMemoryCandleRepository {

    private val candleBuffers: ArrayBuffer[CandleBuffer] = ArrayBuffer()

    def getIterableBuffer(depth: Int): Array[Candle] =
        candleBuffers.flatMap(cb => cb.getCandles(depth)).toArray

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
