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
package org.andstatus.app.appwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.MyLog

/**
 * The configuration screen for the [MyAppWidgetProvider] widget.
 */
class MyAppWidgetConfigure : Activity() {
    var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    var mAppWidgetTitle: EditText? = null
    var appWidgetData: MyAppWidgetData? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
         MyContextHolder.myContextHolder.initialize(this)

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if they press the back button.
        setResult(RESULT_CANCELED)

        // Set the view layout resource to use.
        setContentView(R.layout.appwidget_configure)

        // Find the EditText
        mAppWidgetTitle = findViewById(R.id.appwidget_title)

        // Bind the action for the save button.
        findViewById<View?>(R.id.ok_button).setOnClickListener(mOnClickListener)
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        MyLog.v(TAG) { "mAppWidgetId=$mAppWidgetId" }

        // If they gave us an intent without the widget id, just bail.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
        }
        appWidgetData = MyAppWidgetData.newInstance(NotificationEvents.newInstance(), mAppWidgetId).also {
            // For now we have only one setting to configure:
            mAppWidgetTitle?.setText(it.nothingPref)
        }
    }

    var mOnClickListener: View.OnClickListener? = View.OnClickListener { // When the button is clicked, save configuration settings in our prefs
        // and return that they clicked OK.
        appWidgetData?.nothingPref = mAppWidgetTitle?.getText().toString()
        appWidgetData?.clearCounters()
        appWidgetData?.save()

        // Push widget update to surface with newly set prefix
        val appWidgetIds = intArrayOf(mAppWidgetId)
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_IDS,
                appWidgetIds)
        sendBroadcast(intent)

        // Make sure we pass back the original appWidgetId
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                mAppWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    companion object {
        val TAG: String = MyAppWidgetConfigure::class.java.simpleName
    }
}
