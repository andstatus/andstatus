/*
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;

/**
 * A widget provider. It uses MyAppWidgetData to store preferences and to
 * accumulate data (notifications...) received.
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProvider extends AppWidgetProvider {
    /**
     * Intent with this action sent when it is time to update AppWidget.
     * <p>
     * This may be sent in response to some new information is ready for
     * notification (some changes...), or the system booting.
     * <p>
     * The intent will contain the following extras:
     * <ul>
     * <li>{@link IntentExtra#EXTRA_MSGTYPE}</li>
     * <li>{@link IntentExtra#EXTRA_NUMTWEETSMSGTYPE}</li>
     * 
     * @see AppWidgetProvider#onUpdate AppWidgetProvider.onUpdate(Context
     *      context, AppWidgetManager appWidgetManager, int[] appWidgetIds)
     */
    public static final String ACTION_APPWIDGET_UPDATE = IntentExtra.MY_ACTION_PREFIX + "APPWIDGET_UPDATE";

    private static Object xlock = new Object();
    private final int instanceId = InstanceId.next();

    @Override
    public void onReceive(Context context, Intent intent) {
        final String method = "onReceive";
        String action = intent.getAction();
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + "; action=" + action + "; instanceId=" + instanceId);
        }
        if (MyAppWidgetProvider.ACTION_APPWIDGET_UPDATE.equals(action)) {
            updateData(context, intent);
            updateAllWidgets(context);
        } else {
            super.onReceive(context, intent);
        }
    }
    
    private void updateData(Context context, Intent intent) {
        final String method = "updateData";
        if (!intent.hasExtra(IntentExtra.EXTRA_MSGTYPE.key)) {
            MyLog.d(this, method + "; No EXTRA_MSGTYPE; instanceId=" + instanceId);
            return;
        }
        CommandEnum msgType = CommandEnum.load(intent.getStringExtra(IntentExtra.EXTRA_MSGTYPE.key));
        int numSomethingReceived = intent.getExtras()
                .getInt(IntentExtra.EXTRA_NUMTWEETS.key);
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + "; msgType=" + msgType + "; numMessages=" + numSomethingReceived);
        }

        for (int id : getAppWidgetIds(context)) {
            synchronized(xlock) {
                MyAppWidgetData data = MyAppWidgetData.newInstance(context, id);
                data.update(numSomethingReceived, msgType);
            }
        }
    }

    int[] getAppWidgetIds(Context context) {
        return AppWidgetManager
                        .getInstance(context)
                        .getAppWidgetIds(
                                new ComponentName(context, this.getClass()));
    }
    
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onUpdate; ids=" + Arrays.toString(appWidgetIds));
        }
        for (int id : appWidgetIds) {
            updateView(context, appWidgetManager, id);
        }
    }

    private void updateView(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId) {
        final String method = "updateView";
        try {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + "; Started id=" + appWidgetId);
            }
            MyAppWidgetData widgetData = MyAppWidgetData.newInstance(context, appWidgetId);
            MyRemoteViewData viewData = MyRemoteViewData.fromViewData(context, widgetData);
            RemoteViews views = constructRemoteViews(context, viewData);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        } catch (Exception e) {
            MyLog.e(this, method + "; instanceId=" + instanceId, e);
        }
    }

    /**  Construct the RemoteViews object. It takes the package name (in our case, it's our
     *   package, but it needs this because on the other side it's the widget
     *   host inflating the layout from our package).  */
    private RemoteViews constructRemoteViews(Context context, MyRemoteViewData viewData) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.appwidget);
        if (TextUtils.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.GONE);
        }
        if (TextUtils.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment, android.view.View.GONE);
        }
        if (!TextUtils.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_text, viewData.widgetText);
        }
        if (!TextUtils.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment,
                    android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_comment, viewData.widgetComment);
        }
        views.setTextViewText(R.id.appwidget_time, viewData.widgetTime);
        views.setOnClickPendingIntent(R.id.widget, viewData.onClickIntent);
        return views;
    }

    private void updateAllWidgets(Context context) {
        onUpdate(context, AppWidgetManager.getInstance(context), getAppWidgetIds(context)); 
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onDeleted; ids=" + Arrays.toString(appWidgetIds));
        }
        // When the user deletes the widget, delete all data, associated with it.
        for (int id : appWidgetIds) {
            MyAppWidgetData.newInstance(context, id).delete();
        }
    }

    @Override
    public void onEnabled(Context context) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onEnabled");
        }
    }

    @Override
    public void onDisabled(Context context) {
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "onDisabled");
        }

    }
}
