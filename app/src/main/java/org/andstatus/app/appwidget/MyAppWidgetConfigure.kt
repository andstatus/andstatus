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
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import org.andstatus.app.R;
import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.util.MyLog;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * The configuration screen for the {@link MyAppWidgetProvider} widget.
 */
public class MyAppWidgetConfigure extends Activity {
    static final String TAG = MyAppWidgetConfigure.class.getSimpleName();

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    EditText mAppWidgetTitle;
    MyAppWidgetData appWidgetData;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myContextHolder.initialize(this);

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if they press the back button.
        setResult(RESULT_CANCELED);

        // Set the view layout resource to use.
        setContentView(R.layout.appwidget_configure);

        // Find the EditText
        mAppWidgetTitle = findViewById(R.id.appwidget_title);

        // Bind the action for the save button.
        findViewById(R.id.ok_button).setOnClickListener(mOnClickListener);

        mAppWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        MyLog.v(TAG, () -> "mAppWidgetId=" + mAppWidgetId);

        // If they gave us an intent without the widget id, just bail.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        appWidgetData = MyAppWidgetData.newInstance(NotificationEvents.newInstance(), mAppWidgetId);
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

}
