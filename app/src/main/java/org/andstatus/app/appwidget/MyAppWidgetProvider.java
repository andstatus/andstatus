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
import android.content.Context;
import android.content.Intent;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;

/**
 * A widget provider. It uses MyAppWidgetData to store preferences and to
 * accumulate data (notifications...) received.
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProvider extends AppWidgetProvider {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        MyLog.i(this, "onReceive; action=" + intent.getAction());
        MyContextHolder.initialize(context, this);
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        MyLog.v(this, "onUpdate; ids=" + Arrays.toString(appWidgetIds));
        AppWidgets appWidgets = AppWidgets.newInstance(MyContextHolder.get());
        for (int id : appWidgetIds) {
            appWidgets.updateView(appWidgetManager, id);
        }
    }
    
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        MyLog.v(this, "onDeleted; ids=" + Arrays.toString(appWidgetIds));
        // When the user deletes the widget, delete all data, associated with it.
        for (int id : appWidgetIds) {
            MyAppWidgetData.newInstance(context, id).delete();
        }
    }

    @Override
    public void onEnabled(Context context) {
        MyLog.v(this, "onEnabled");
    }

    @Override
    public void onDisabled(Context context) {
        MyLog.v(this, "onDisabled");
    }
}
