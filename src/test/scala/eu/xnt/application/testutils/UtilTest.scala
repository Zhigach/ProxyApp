package eu.xnt.application.testutils

import eu.xnt.application.UnitTestSpec
import eu.xnt.application.model.Quote

import java.nio.ByteBuffer
import scala.language.postfixOps

class UtilTest extends UnitTestSpec {

    "Util" should "encode quote that it could be read correctly" in {
        val tickerName = "TST.TST"
        val quoteBuffer = Util.encodeQuote(Util.randomQuote(ticker = tickerName))
        val q = Quote.parse(ByteBuffer.wrap(quoteBuffer)).get
        println(q)
        q.toString should include regex tickerName.r
    }
}
