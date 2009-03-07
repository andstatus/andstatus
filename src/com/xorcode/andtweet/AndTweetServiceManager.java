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

package com.xorcode.andtweet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * @author torgny.bjers
 *
 */
public class AndTweetServiceManager extends BroadcastReceiver {

	public static final String TAG = "AndTweetServiceManager";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
			// Set up the alarm manager
			int mFrequency = 180;
			AlarmManager mAM = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			Intent serviceIntent = new Intent(IAndTweetService.class.getName());
			PendingIntent mAlarmSender = PendingIntent.getService(context, 0, serviceIntent, 0);
			mAM.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), mFrequency * 1000, mAlarmSender);
		} else {
			Log.d(TAG, "Received unexpected intent: " + intent.toString());
		}
	}

}
