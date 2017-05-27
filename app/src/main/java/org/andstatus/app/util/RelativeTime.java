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

package org.andstatus.app.util;

import android.content.Context;

import org.andstatus.app.R;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author yvolk@yurivolkov.com
 */
public class RelativeTime {

    private RelativeTime() {
        // Empty
    }

    /**
     * Difference to Now
     */
    public static String getDifference(Context context, long fromMs) {
        return getDifference(context, fromMs, System.currentTimeMillis());
    }

    public static String getDifference(Context context, long fromMs, long toMs) {
        if (fromMs <= 0 || toMs <= 0) {
            return "";
        }
        long numSeconds = TimeUnit.MILLISECONDS.toSeconds(toMs - fromMs);
        if (numSeconds < 1) {
            return context.getString(R.string.reltime_just_now);
        }
        long numMinutes = TimeUnit.SECONDS.toMinutes(numSeconds);
        if (numMinutes < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numSeconds,
                    R.array.reltime_seconds_ago_patterns,
                    R.array.reltime_seconds_ago_formats);
        }
        long numHours = TimeUnit.SECONDS.toHours(numSeconds);
        if (numHours < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numMinutes,
                    R.array.reltime_minutes_ago_patterns,
                    R.array.reltime_minutes_ago_formats);
        }
        long numDays = TimeUnit.SECONDS.toDays(numSeconds);
        if (numDays < 2) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numHours,
                    R.array.reltime_hours_ago_patterns,
                    R.array.reltime_hours_ago_formats);
        }
        long numMonths = getMonthsDifference(fromMs, toMs);
        if (numMonths < 3) {
            return I18n.formatQuantityMessage(context,
                    0,
                    numDays,
                    R.array.reltime_days_ago_patterns,
                    R.array.reltime_days_ago_formats);
        }
        // TODO: Years...
        return I18n.formatQuantityMessage(context,
                0,
                numMonths,
                R.array.reltime_months_ago_patterns,
                R.array.reltime_months_ago_formats);
    }

    private static long getMonthsDifference(long fromMs, long toMs) {
        if (fromMs <= 0 || toMs <= fromMs ) {
            return 0;
        }
        // TODO: Migrate to java.util.time, see http://stackoverflow.com/questions/1086396/java-date-month-difference
        Date date1 = new Date(fromMs);
        Date date2 = new Date(toMs);
        return (date2.getYear() - date1.getYear()) * 12L + (date2.getMonth() - date1.getMonth());
    }

    /** Returns false if previousTime <= 0 */
    public static boolean wasButMoreSecondsAgoThan(long previousTime, long predefinedPeriodSeconds) {
        return previousTime > 0 && secondsAgo(previousTime) > predefinedPeriodSeconds;
    }

    public static boolean moreSecondsAgoThan(long previousTime, long predefinedPeriodSeconds) {
        return secondsAgo(previousTime) > predefinedPeriodSeconds;
    }

    public static long secondsAgo(long previousTime) {
        return java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()
                - previousTime);
    }
}
