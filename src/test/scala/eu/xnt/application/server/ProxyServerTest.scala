package eu.xnt.application.server

import eu.xnt.application.repository.CandleBuffer
import org.scalatest.wordspec.AnyWordSpecLike


class ProxyServerTest extends AnyWordSpecLike {
    private val candleBuffer = CandleBuffer("TEST.TEST", 60000)

    "ProxyServer" must {

        "connect to a source feed" in {

        }

        "receive quotes" in {

        }

        "provide historical data to a newly connected client" in {

        }

        "send new candle at the start of a new minute" in {

        }


    }
}
