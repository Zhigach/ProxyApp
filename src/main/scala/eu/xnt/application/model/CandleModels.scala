package eu.xnt.application.model

object CandleModels {

    final case class Candle(ticker: String,
                      timestamp: Long,
                      duration: Int = 60000,
                      open: Double,
                      high: Double,
                      low: Double,
                      close: Double,
                      volume: Int) extends JsonSupport //TODO это что ли "кейс класс обычно не наследуют от json трейтов"

    /**
     * Adds quote to an existing candle
     * @param quote quote to add
     * @param candle candle to update
     * @return updated Candle
     */
    def updateCandle(quote: Quote, candle: Candle): Candle = {
        val price = quote.price
        val h = math.max(candle.high, price)
        val l = math.min(candle.low, price)
        val vol = candle.volume + quote.size
        candle.copy(high = h, low = l, volume = vol, close=quote.price)
    }

    /**
     * Create new candle from a quote
     * @return
     */
    def newCandleFromQuote(quote: Quote, duration: Int = 60000): Candle = {
        Candle(
            ticker = quote.ticker,
            timestamp = (quote.timestamp / duration) * duration,
            open = quote.price,
            high = quote.price,
            low = quote.price,
            close = quote.price,
            volume = quote.size
        )
    }

}
