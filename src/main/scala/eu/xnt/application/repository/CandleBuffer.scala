package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.{Candle, CandleResponse}
import eu.xnt.application.model.{CandleModels, Quote}

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class CandleBuffer(val ticker: String) extends Iterable[Candle] {

    private val buffer: mutable.Stack[Candle] = mutable.Stack()
    /**
     * 0) если свечек для инструмента  нет, то создаём первую свечу
     * 1) Если таймстемп котировки позднее (таймстемп + длительности последней свечки), то создаем новую
     * 2) если таймстемп попадает в последнюю свечу, то обновляем её и подкладываем на место последней свечи в хранилище
     */
    def addQuote(quote: Quote): Unit = {
        if buffer.isEmpty then
            buffer.push(CandleModels.newCandleFromQuote(quote)) // 0
        else
            val lastCandle = buffer.head
            val quoteTS: Long = quote.timestamp
            val candleEndTS: Long = lastCandle.timestamp + lastCandle.duration
            if quoteTS.compareTo(candleEndTS) > 0 then
                buffer.push(CandleModels.newCandleFromQuote(quote)) // 1
            else
                buffer.push(CandleModels.updateCandle(quote, buffer.pop())) // 2
    }

    def getCandles(limit: Int): Array[Candle] = {
        buffer.takeRight(limit).toArray
    }

    override def toString: String =        
        CandleResponse(buffer.toArray).toString

    override def iterator: Iterator[Candle] = buffer.iterator
}
