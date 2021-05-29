/* 
 * Copyright (C) 2010-2017 Yuri Volkov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.util

import android.content.Context
import org.andstatus.app.R
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author yvolk@yurivolkov.com
 */
object RelativeTime {
    const val DATETIME_MILLIS_NEVER: Long = 0
    const val SOME_TIME_AGO: Long = 1 // We don't know exact date

    /**
     * Difference to Now
     */
    fun getDifference(context: Context, fromMs: Long): String {
        return getDifference(context, fromMs, System.currentTimeMillis())
    }

    fun getDifference(context: Context, fromMs: Long, toMs: Long): String {
        if (fromMs <= DATETIME_MILLIS_NEVER || toMs <= DATETIME_MILLIS_NEVER) return ""
        if (fromMs == SOME_TIME_AGO) return context.getString(R.string.reltime_some_time_ago)
        val numSeconds = TimeUnit.MILLISECONDS.toSeconds(toMs - fromMs)
        if (numSeconds < 1) {
            return context.getString(R.string.reltime_just_now)
        }
        val numMinutes = TimeUnit.SECONDS.toMinutes(numSeconds)
        if (numMinutes < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numSeconds,
                    R.array.reltime_seconds_ago_patterns,
                    R.array.reltime_seconds_ago_formats)
        }
        val numHours = TimeUnit.SECONDS.toHours(numSeconds)
        if (numHours < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numMinutes,
                    R.array.reltime_minutes_ago_patterns,
                    R.array.reltime_minutes_ago_formats)
        }
        val numDays = TimeUnit.SECONDS.toDays(numSeconds)
        if (numDays < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numHours,
                    R.array.reltime_hours_ago_patterns,
                    R.array.reltime_hours_ago_formats)
        }
        val numMonths = getMonthsDifference(fromMs, toMs)
        return if (numMonths < 3) {
            I18n.formatQuantityMessage(context,
                    0,
                    numDays,
                    R.array.reltime_days_ago_patterns,
                    R.array.reltime_days_ago_formats)
        } else I18n.formatQuantityMessage(context,
                0,
                numMonths,
                R.array.reltime_months_ago_patterns,
                R.array.reltime_months_ago_formats)
        // TODO: Years...
    }

    private fun getMonthsDifference(fromMs: Long, toMs: Long): Long {
        if (fromMs <= DATETIME_MILLIS_NEVER || toMs <= fromMs) {
            return 0
        }
        // TODO: Migrate to java.util.time, see http://stackoverflow.com/questions/1086396/java-date-month-difference
        val date1 = Date(fromMs)
        val date2 = Date(toMs)
        return (date2.year - date1.year) * 12L + (date2.month - date1.month)
    }

    /** Returns false if previousTime <= 0  */
    fun wasButMoreSecondsAgoThan(previousTime: Long, predefinedPeriodSeconds: Long): Boolean {
        return previousTime > 0 && secondsAgo(previousTime) > predefinedPeriodSeconds
    }

    fun moreSecondsAgoThan(previousTime: Long, predefinedPeriodSeconds: Long): Boolean {
        return secondsAgo(previousTime) > predefinedPeriodSeconds
    }

    fun secondsAgo(previousTime: Long): Long {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()
                - previousTime)
    }

    /** Minimum, but real date  */
    fun minDate(date1: Long, date2: Long): Long {
        return if (date1 > SOME_TIME_AGO) if (date2 > SOME_TIME_AGO) java.lang.Long.min(date1, date2) else date1 else if (date2 > SOME_TIME_AGO) date2 else java.lang.Long.max(date1, date2)
    }
}
