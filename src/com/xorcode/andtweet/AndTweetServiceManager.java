/**
 * 
 */
package com.xorcode.andtweet;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
			ComponentName component = new ComponentName(context, AndTweetService.class);
			ComponentName service = context.startService(new Intent().setComponent(component));
			if (service == null) {
				Log.e(TAG, "Could not start service " + component.toString());
			}
		} else {
			Log.d(TAG, "Received unexpected intent: " + intent.toString());
		}
	}

}
