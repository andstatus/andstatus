package org.andstatus.app.util

/**
 * @author yvolk@yurivolkov.com
 */
class StopWatch : org.apache.commons.lang3.time.StopWatch() {
    fun hasPassed(millis: Long): Boolean {
        if (time > millis) {
            restart()
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
    }
}