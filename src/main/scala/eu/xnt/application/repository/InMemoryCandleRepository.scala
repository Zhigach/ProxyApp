package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.Quote

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

case class InMemoryCandleRepository(candleDuration: Long) {

    private val candleBuffers: ArrayBuffer[CandleBuffer] = ArrayBuffer()

    def getHistoricalCandles(depth: Int = 1): Array[Candle] = {
        candleBuffers.flatMap(cb => cb.getHistoricalCandles(depth)).toArray
    }

    def addQuote(quote: Quote, buffer: CandleBuffer): Unit = {
        buffer.addQuote(quote)
    }

    def addQuote(q: Quote): Unit = {        
        val ticker = q.ticker
        val optBuffer = getBuffer(ticker)
        optBuffer match {
            case Some(buffer) =>
                addQuote(q, buffer)
            case None => // если буфера для такого тикера нет, то создаем новый                
                candleBuffers.addOne(CandleBuffer(ticker, candleDuration))
                val newBuffer = getBuffer(ticker)
                addQuote(q, newBuffer.get)
        }
    }

    private def getBuffer(ticker: String): Option[CandleBuffer] =
        candleBuffers.find(b => b.ticker == ticker)
}
