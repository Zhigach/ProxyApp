package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.Quote

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

/**
 * Storage for candles. Can hold candle for numerous instruments
 * @param candleDuration candle timeframe duration
 */
class InMemoryCandleRepository(candleDuration: Long) {

    private val candleBuffers: ArrayBuffer[CandleBuffer] = ArrayBuffer()

    /**
     * Get number of stored candles (completed or not) for specified ticker
     * @param ticker name of an instrument
     */
    def bufferSize(ticker: String): Int = {
        candleBuffers.find(cb => cb.ticker == ticker) match {
            case Some(buffer) => buffer.size
            case None => 0
        }
    }

    /**
     * Get completed historical candles from present moment with specified depth
     * @param depth depth of history in minutes
     * @return historical candles which are not older that specified depth in minutes for all tickers in storage
     */
    def getHistoricalCandles(depth: Int = 1): Vector[Candle] = {
        candleBuffers.flatMap(cb => cb.getHistoricalCandles(depth)).toVector
    }

    private def addQuote(quote: Quote, buffer: CandleBuffer): Unit = {
        buffer.addQuote(quote)
    }

    /**
     * Add quote to the storage
     */
    def addQuote(q: Quote): Unit = {        
        val ticker = q.ticker
        val optBuffer = getBuffer(ticker)
        optBuffer match {
            case Some(buffer) =>
                addQuote(q, buffer)
            case None => // если буфера для такого тикера нет, то создаем новый
                candleBuffers.addOne(new CandleBuffer(ticker, candleDuration))
                val newBuffer = getBuffer(ticker)
                addQuote(q, newBuffer.get)
        }
    }

    private def getBuffer(ticker: String): Option[CandleBuffer] =
        candleBuffers.find(b => b.ticker == ticker)
}
