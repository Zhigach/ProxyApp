package eu.xnt.application.repository

import eu.xnt.application.UnitTestSpec
import eu.xnt.application.testutils.Util.randomQuote


class CandleBufferTest extends UnitTestSpec {

    private val candleBuffer = new CandleBuffer("TEST.TEST", 60000)
    private val ts = (System.currentTimeMillis() / 60000 - 2) * 60000 + 1 // make two minutes old timestamp

    "CandleBuffer" should "add quote" in {
        candleBuffer.addQuote(randomQuote(ts))
        candleBuffer.buffer should have size 1
    }

    it should "change old candle if this is the same minute" in {
        val candle = candleBuffer.buffer.head
        candleBuffer.addQuote(randomQuote(ts + 1))
        val newCandle = candleBuffer.buffer.head
        newCandle should not be candle
    }

    it should "create new candle if new quote is in a new minute" in {
        candleBuffer.addQuote(randomQuote(ts + 60000))
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
