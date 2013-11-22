/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.andstatus.app.appwidget;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

/**
 * The configuration screen for the MyAppWidgetProvider widget.
 */
public class MyAppWidgetConfigure extends Activity {
	static final String TAG = MyAppWidgetConfigure.class.getSimpleName();

	int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	EditText mAppWidgetTitle;
	MyAppWidgetData appWidgetData;

	public MyAppWidgetConfigure() {
		super();
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		// Set the result to CANCELED. This will cause the widget host to cancel
		// out of the widget placement if they press the back button.
		setResult(RESULT_CANCELED);

		// Set the view layout resource to use.
		setContentView(R.layout.appwidget_configure);

		// Find the EditText
		mAppWidgetTitle = (EditText) findViewById(R.id.appwidget_title);

		// Bind the action for the save button.
		findViewById(R.id.save_button).setOnClickListener(mOnClickListener);

		// Find the widget id from the intent.
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "mAppWidgetId=" + mAppWidgetId);
        }

		// If they gave us an intent without the widget id, just bail.
		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finish();
		}

		appWidgetData = new MyAppWidgetData(this,
				mAppWidgetId);
		appWidgetData.load();
		
		// For now we have only one setting to configure:
		mAppWidgetTitle.setText(appWidgetData.nothingPref);
	}

	View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
        public void onClick(View v) {
			// When the button is clicked, save configuration settings in our prefs
			// and return that they clicked OK.
			appWidgetData.nothingPref = mAppWidgetTitle.getText().toString();
			appWidgetData.clearCounters();
			appWidgetData.save();

			// Push widget update to surface with newly set prefix
			int[] appWidgetIds = { mAppWidgetId };
			Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			intent.putExtra(
					android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS,
					appWidgetIds);
			sendBroadcast(intent);

			// Make sure we pass back the original appWidgetId
			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
					mAppWidgetId);
			setResult(RESULT_OK, resultValue);
			finish();
		}
	};
	
	/**
	 * Delete all Widgets
	 * Idea from <a href="http://stackoverflow.com/a/7774503">Removing AppWidgets programmatically</a>
	 * @param context
	 * @param packageName
	 * @param className
	 * @return
	 */
    static public int deleteWidgets(Context context, String packageName, String className) {
        int deletedCount = 0;
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = awm.getAppWidgetIds(new android.content.ComponentName(packageName, 
                    className)); 
            MyLog.i(TAG, "About to delete " + appWidgetIds.length +" Widgets of " + packageName + " package; class=" + className);
            AppWidgetHost host = new AppWidgetHost(context, 0);
            for (int ind = 0; ind < appWidgetIds.length; ind++) {
                host.deleteAppWidgetId(appWidgetIds[ind]);         
            }
        } catch (Exception e) {
            MyLog.e(TAG, "Error deleting widgets", e);
        }
        return deletedCount;
    }
	
}
