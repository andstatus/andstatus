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

package com.xorcode.andtweet.util;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Date;

import com.xorcode.andtweet.R;

import android.content.Context;

/**
 * 
 * @author torgny.bjers
 */
public class RelativeTime {

	private static final int SECOND = 1;
	private static final int MINUTE = 60 * SECOND;
	private static final int HOUR = 60 * MINUTE;
	private static final int DAY = 24 * HOUR;
	private static final int MONTH = 30 * DAY;

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
	 * 
	 * @param from
	 * @return String
	 */
	public static String getDifference(Context context, long from) {
		String value = new String();
		long to = System.currentTimeMillis();
		long delta = (to - from) / 1000;
		if (delta < 0) {
			value = context.getString(R.string.reltime_just_now);
		} else if (delta < 1 * MINUTE) {
			value = context.getString(R.string.reltime_seconds_ago, new Object[] { delta });
		} else if (delta < 2 * MINUTE) {
			value = context.getString(R.string.reltime_a_minute_ago);
		} else if (delta < 59 * MINUTE) {
			value = context.getString(R.string.reltime_minutes_ago, new Object[] { delta / MINUTE });
		} else if (delta < 90 * MINUTE) {
			value = context.getString(R.string.reltime_an_hour_ago);
		} else if (delta < 150 * MINUTE) {
			value = context.getString(R.string.reltime_two_hours_ago);
		} else if (delta < 24 * HOUR) {
			value = context.getString(R.string.reltime_hours_ago, new Object[] { delta / HOUR });
		} else if (delta < 48 * HOUR) {
			value = context.getString(R.string.reltime_yesterday);
		} else if (delta < 30 * DAY) {
			value = context.getString(R.string.reltime_days_ago, new Object[] { delta / DAY });
		} else if (delta < 12 * MONTH) {
			MessageFormat form = new MessageFormat("{0}");
			Object[] formArgs = new Object[] { delta / MONTH };
			double[] tweetLimits = {1,2};
			String[] tweetPart = { context.getString(R.string.reltime_one_month_ago), context.getString(R.string.reltime_months_ago) };
			ChoiceFormat tweetForm = new ChoiceFormat(tweetLimits, tweetPart);
			form.setFormatByArgumentIndex(0, tweetForm);
			value = form.format(formArgs);
		}
		return value;
	}

	/**
	 * Returns the relative time passed since now.
	 * 
	 * @return String
	 */
	public String toString() {
		return getDifference(mContext, mCalendar.getTimeInMillis());
	}
}
