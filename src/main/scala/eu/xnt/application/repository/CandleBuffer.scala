package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.{Candle, CandleResponse}
import eu.xnt.application.model.{CandleModels, Quote}
import eu.xnt.application.utils.Math._

import scala.collection.mutable

/**
 * Storage for a single ticker candles
 * @param ticker unique name of an instrument
 * @param duration single Candle duration
 */
case class CandleBuffer(ticker: String, duration: Long) extends Iterable[Candle] {

    val buffer: mutable.Stack[Candle] = mutable.Stack()

    /**
     * Add single quote to the storage
     */
    def addQuote(quote: Quote): Unit = {
        if (buffer.isEmpty) {
            buffer.push(CandleModels.newCandleFromQuote(quote)) // 0
        } else {
            val lastCandle = buffer.head
            val quoteTS: Long = quote.timestamp
            val candleEndTS: Long = lastCandle.timestamp + lastCandle.duration
            if (quoteTS.compareTo(candleEndTS) > 0) {
              buffer.push(CandleModels.newCandleFromQuote(quote)) // 1
            } else
                buffer.push(CandleModels.updateCandle(quote, buffer.pop())) // 2
        }
    }

    /**
     * Get candles that are older than present time by specified depth
     * @param depth maximum age of a quote in minutes
     * @return
     */
    def getHistoricalCandles(depth: Int): Array[Candle] = {
        val currentTimeMillisRounded = roundBy(System.currentTimeMillis(), duration)
        val lowerLimitMillis = currentTimeMillisRounded - depth * duration
        val result = buffer
          .filter(c => (c.timestamp >= lowerLimitMillis) && c.timestamp < currentTimeMillisRounded)
          .toArray
        result
    }

    override def toString: String =        
        CandleResponse(buffer.toArray).toString

    override def iterator: Iterator[Candle] = buffer.iterator
}
