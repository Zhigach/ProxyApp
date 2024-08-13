package eu.xnt.application.utils

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.language.postfixOps

object Math {
    
    def roundBy(long: Long, to: Long): Long = {
        (long / to) * to
    }

    def millisToNewTimeframe(timeframeMillisDuration: Int = 60000): FiniteDuration = {
        val currentTimeMillis = System.currentTimeMillis()
        val delayToFirstExecution = roundBy(currentTimeMillis, timeframeMillisDuration) + timeframeMillisDuration - currentTimeMillis
        delayToFirstExecution millis
    }

}
