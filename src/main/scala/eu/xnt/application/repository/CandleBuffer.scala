package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.Candle
import eu.xnt.application.model.{CandleModels, Quote}

import java.time.{Instant, LocalDateTime, ZoneId}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class CandleBuffer(val ticker: String) {

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
            val quoteTS = LocalDateTime.ofInstant(Instant.ofEpochMilli(quote.timestamp), ZoneId.of("UTC"))
            val candleEndTS = lastCandle.timestamp.plus(scala.jdk.javaapi.DurationConverters.toJava(lastCandle.duration))
            if quoteTS.compareTo(candleEndTS) > 0 then
                buffer.push(CandleModels.newCandleFromQuote(quote)) // 1
            else
                buffer.push(CandleModels.updateCandle(quote, buffer.pop())) // 2
    }

    def getCandles(limit: Int): Array[Candle] = {
        buffer.takeRight(limit).toArray
    }

    override def toString: String =
        String(s"<${ticker}>: ${(for (candle <- buffer) yield candle).mkString(", ")}")
}
