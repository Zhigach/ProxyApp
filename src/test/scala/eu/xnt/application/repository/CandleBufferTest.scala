package eu.xnt.application.repository

import eu.xnt.application.repository.testutils.Util.randomQuote
import org.scalatest.wordspec.AnyWordSpecLike


class CandleBufferTest extends AnyWordSpecLike {
    private val candleBuffer = CandleBuffer("TEST.TEST", 60000)
    private val ts = (System.currentTimeMillis() / 60000 - 2) * 60000 + 1 // make two minutes

    "CandleBuffer" must {
        "add quote" in {
            candleBuffer.addQuote(randomQuote(ts))
            assert(candleBuffer.buffer.size == 1)
        }
        "change old candle if this is the same minute" in {
            val candle = candleBuffer.buffer.head
            candleBuffer.addQuote(randomQuote(ts + 1))
            val newCandle = candleBuffer.buffer.head
            assert(newCandle != candle)
        }
        "create new candle if new quote is in a new minute" in {
            candleBuffer.addQuote(randomQuote(ts + 60000))
            assert(candleBuffer.buffer.size == 2)
        }
        "return historical quote that was added in previous minute" in {
            assert(candleBuffer.getHistoricalCandles(1).length == 1)
        }
        "return all historical quotes if requested depth is greater than real history size" in {
            assert(candleBuffer.getHistoricalCandles(2).length == 2)
        }
        "return valid real candle history size" in {
            assert(candleBuffer.getHistoricalCandles(3).length == 2)
        }
    }




}
