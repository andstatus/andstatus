package org.andstatus.app.util

import kotlinx.coroutines.delay

/**
 * @author yvolk@yurivolkov.com
 */
class StopWatch : org.apache.commons.lang3.time.StopWatch() {

    fun notPassed(millis: Long): Boolean = !hasPassed(millis)

    fun notPassedSeconds(seconds: Int): Boolean = notPassed(seconds * 1000L)

    fun hasPassed(millis: Long): Boolean {
        if (time > millis) {
            return true
        }
        return false
    }

    fun restart() {
        reset()
        start()
    }

    companion object {
        fun createStarted(): StopWatch {
            val sw = StopWatch()
            sw.start()
            return sw
        }

        suspend inline fun tillPassedSeconds(seconds: Int, block: () -> Boolean) {
            val stopWatch = createStarted()
            do {
                if (block()) break
                delay(10)
            } while (stopWatch.notPassedSeconds(seconds))
        }
    }
}
