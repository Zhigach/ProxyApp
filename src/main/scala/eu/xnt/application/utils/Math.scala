package eu.xnt.application.utils

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

object Math {
    
    def roundBy(long: Long, to: Long): Long = {
        (long / to) * to
    }

    def millisToNewTimeframe(timeframeMillisDuration: Int = 60000): FiniteDuration = {
        val currentTimeMillis = System.currentTimeMillis()
        val delayToFirstExecution = roundBy(currentTimeMillis, timeframeMillisDuration) + timeframeMillisDuration - currentTimeMillis
        Duration.create(delayToFirstExecution, TimeUnit.MILLISECONDS)
    }

}
