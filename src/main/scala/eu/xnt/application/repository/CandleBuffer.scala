package eu.xnt.application.repository

import eu.xnt.application.model.CandleModels.{Candle, CandleResponse}
import eu.xnt.application.model.{CandleModels, Quote}
import eu.xnt.application.server.ProxyServer.repository

import scala.collection.mutable

class CandleBuffer(val ticker: String, val duration: Long) extends Iterable[Candle] {

    val buffer: mutable.Stack[Candle] = mutable.Stack()
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

    def getHistoricalCandles(depth: Int): Array[Candle] = {
        //val currentTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.of("UTC"))
        val currentTimeMillis = System.currentTimeMillis()
        println(s"Candle history with depth of $depth for $ticker requested at $currentTimeMillis")
        val lowerLimitMillis = currentTimeMillis - depth * 60000
        val upperLimitMillis = (currentTimeMillis / 60000) * 60000
        val result = buffer.filter(c => (c.timestamp >= lowerLimitMillis) &&
                            c.timestamp < upperLimitMillis)
          .toArray
        println(s"Found ${result.length} => ${result.mkString}")
        result
    }

    def getCandles(limit: Int): Array[Candle] = {
        buffer.takeRight(limit).toArray
    }

    override def toString: String =        
        CandleResponse(buffer.toArray).toString

    override def iterator: Iterator[Candle] = buffer.iterator
}
