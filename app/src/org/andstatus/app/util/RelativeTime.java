/* 
 * Copyright (C) 2008 Torgny Bjers
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

import java.util.Calendar;
import java.util.Date;

import org.andstatus.app.R;

import android.content.Context;

/**
 * 
 * @author torgny.bjers
 * 2010-03-22 yvolk improved phrase formatting to using {@link I18n#formatQuantityMessage} 
 */
public class RelativeTime {

    private static final int SECOND = 1;
    private static final int MINUTE = 60 * SECOND;
    private static final int HOUR = 60 * MINUTE;
    private static final int DAY = 24 * HOUR;
    private static final int MONTH = 30 * DAY;
    public static final long SECONDS_IN_A_DAY = DAY;

    private Calendar mCalendar;
    private Context mContext;

    /**
     * 
     * @param cal
     */
    public RelativeTime(Calendar cal, Context context) {
        mCalendar = cal;
        mContext = context;
    }

    /**
     * 
     * @param date
     */
    public RelativeTime(Date date) {
        mCalendar = Calendar.getInstance();
        mCalendar.setTime(date);
    }

    /**
     * 
     * @param milliseconds
     */
    public RelativeTime(long milliseconds) {
        mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(milliseconds);
    }

    /**
     * Difference to Now
     * @param fromMs 
     * @return String
     */
    public static String getDifference(Context context, long fromMs) {
        String value;
        long toMs = System.currentTimeMillis();
        long deltaSeconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(java.lang.Math.round((double)(toMs - fromMs)));
        if (fromMs <= 0) {
            value = "";
        } else if (deltaSeconds < 1) {
            value = context.getString(R.string.reltime_just_now);
        } else if (deltaSeconds < 1 * MINUTE) {
            int numSeconds = (int) deltaSeconds;
            value = I18n.formatQuantityMessage(context,
                    0,
                    numSeconds,
                    R.array.reltime_seconds_ago_patterns,
                    R.array.reltime_seconds_ago_formats);
        } else if (deltaSeconds < 59 * MINUTE) {
            int numMinutes = (int) java.lang.Math.round( (double) deltaSeconds / MINUTE);
            value = I18n.formatQuantityMessage(context,
                    0,
                    numMinutes,
                    R.array.reltime_minutes_ago_patterns,
                    R.array.reltime_minutes_ago_formats);
        } else if (deltaSeconds < 24 * HOUR) {
            int numHours = (int) java.lang.Math.round( (double) deltaSeconds / HOUR);
            value = I18n.formatQuantityMessage(context,
                    0,
                    numHours,
                    R.array.reltime_hours_ago_patterns,
                    R.array.reltime_hours_ago_formats);
        } else if (deltaSeconds < 30 * DAY) {
            int numDays = (int) java.lang.Math.round( (double) deltaSeconds / DAY);
            value = I18n.formatQuantityMessage(context,
                    0,
                    numDays,
                    R.array.reltime_days_ago_patterns,
                    R.array.reltime_days_ago_formats);
        } else if (deltaSeconds < 12 * MONTH) {
            int numMonths = (int) java.lang.Math.round( (double) deltaSeconds / MONTH);
            value = I18n.formatQuantityMessage(context,
                    0,
                    numMonths,
                    R.array.reltime_months_ago_patterns,
                    R.array.reltime_months_ago_formats);
        } else {
            // TODO: Years...
            value = context.getString(R.string.reltime_years_ago);
        }
        return value;
    }

    /**
     * Returns the relative time passed since now.
     * 
     * @return String
     */
    @Override
    public String toString() {
        return getDifference(mContext, mCalendar.getTimeInMillis());
    }

    public static boolean moreSecondsAgoThan(long previousTime, long predefinedPeriodSeconds) {
        return secondsAgo(previousTime) > predefinedPeriodSeconds;
    }

    public static long secondsAgo(long previousTime) {
        return java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()
                - previousTime);
    }
}
