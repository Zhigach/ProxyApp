package eu.xnt.application.repository

import eu.xnt.application.repository.testutils.Util.randomQuote
import org.scalatest.wordspec.AnyWordSpecLike


class InMemoryCandleRepositoryTest extends AnyWordSpecLike {

    private val inMemoryCandleRepository = new InMemoryCandleRepository(60000)

    private val ts = (System.currentTimeMillis() / 60000 - 2) * 60000 + 1 // make a previous minute ts

    "InMemoryCandleRepository" must {
        "save new quotes" in {
            inMemoryCandleRepository.addQuote(randomQuote(ts, "TEST"))
            assert(inMemoryCandleRepository.bufferSize("TEST") == 1)
        }
        "save quote from another minute to a new quote" in {
            inMemoryCandleRepository.addQuote(randomQuote(ts + 60001, "TEST"))
            assert(inMemoryCandleRepository.bufferSize("TEST") == 2)
        }
        "save quote for different ticker into separate buffer" in {
            inMemoryCandleRepository.addQuote(randomQuote(ts, "TEST2"))
            assert(inMemoryCandleRepository.bufferSize("TEST2") == 1)
            assert(inMemoryCandleRepository.bufferSize("TEST") == 2)
        }
        "return flat array of all tickers in all buffers for a given time depth" in {
            assert(inMemoryCandleRepository.getHistoricalCandles(2).length == 3)
        }

    }

}
