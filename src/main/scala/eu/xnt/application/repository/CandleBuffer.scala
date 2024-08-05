package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
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
            buffer.push(CandleModels.newCandleFromQuote(quote))
        } else {
            val lastCandle = buffer.head
            val quoteTS: Long = quote.timestamp
            val candleEndTS: Long = lastCandle.timestamp + lastCandle.duration
            if (quoteTS.compareTo(candleEndTS) > 0)
                buffer.push(CandleModels.newCandleFromQuote(quote))
            else
                buffer.push(CandleModels.updateCandle(quote, buffer.pop()))
        }
    }

    /**
     * Get candles that are older than present time by specified depth
     * @param depth maximum age of a quote in minutes
     * @return
     */
    def getHistoricalCandles(depth: Int): Vector[Candle] = {
        val currentTimeMillisRounded = roundBy(System.currentTimeMillis(), duration)
        val lowerLimitMillis = currentTimeMillisRounded - depth * duration
        val result = buffer
          .filter(c => (c.timestamp >= lowerLimitMillis) && c.timestamp < currentTimeMillisRounded)
          .toVector
        result
    }

    override def toString: String =        
        buffer.toVector.toString

    override def iterator: Iterator[Candle] = buffer.iterator
}
