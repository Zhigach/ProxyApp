package eu.xnt.application.repository

import eu.xnt.application.UnitTestSpec
import eu.xnt.application.testutils.Util.{oldQuote, randomQuote}


class CandleBufferTest extends UnitTestSpec {

    private val candleBuffer = new CandleBuffer("TEST.TEST", 60000)

    "CandleBuffer" should "add quote" in {
        candleBuffer.addQuote(oldQuote(2))
        candleBuffer.buffer should have size 1
    }

    it should "change old candle if this is the same minute" in {
        val candle = candleBuffer.buffer.head
        candleBuffer.addQuote(oldQuote(2))
        val newCandle = candleBuffer.buffer.head
        newCandle should not be candle
    }

    it should "create new candle if new quote is in a new minute" in {
        candleBuffer.addQuote(oldQuote(1))
        candleBuffer.buffer should have size 2
    }

    it should "return historical quote that was added in previous minute" in {
        candleBuffer.getHistoricalCandles(1) should have length 1
    }

    it should "return all historical quotes if requested depth is greater than real history size" in {
        candleBuffer.getHistoricalCandles(2) should have length 2
    }

    it should "return valid real candle history size" in {
        candleBuffer.getHistoricalCandles(3) should have length 2
    }
}
