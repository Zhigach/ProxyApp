package eu.xnt.application.repository

import eu.xnt.application.UnitTestSpec
import eu.xnt.application.testutils.Util.randomQuote


class InMemoryCandleRepositoryTest extends UnitTestSpec {

    private val inMemoryCandleRepository = new InMemoryCandleRepository(60000)
    private val ts = (System.currentTimeMillis() / 60000 - 2) * 60000 + 1 // make a previous minute ts

    "InMemoryCandleRepository" must "save new quotes" in {
        inMemoryCandleRepository.addQuote(randomQuote(ts, "TEST"))
        inMemoryCandleRepository.bufferSize("TEST") shouldEqual 1
    }

    it should "save quote from another minute to a new quote" in {
        inMemoryCandleRepository.addQuote(randomQuote(ts + 60001, "TEST"))
        inMemoryCandleRepository.bufferSize("TEST") shouldEqual 2
    }

    it should "save quote for different ticker into separate buffer" in {
        inMemoryCandleRepository.addQuote(randomQuote(ts, "TEST2"))
        inMemoryCandleRepository.bufferSize("TEST2") shouldEqual 1
        inMemoryCandleRepository.bufferSize("TEST") shouldEqual 2
    }

    it should "return flat array of all tickers in all buffers for a given time depth" in {
        inMemoryCandleRepository.getHistoricalCandles(2) should have length 3
    }
}
