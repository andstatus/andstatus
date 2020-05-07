/*
 * Copyright (C) 2010-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;
import java.util.function.Predicate;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * A widget provider. It uses {@link MyAppWidgetData} to store preferences and to
 * accumulate data (notifications...) received.
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = MyAppWidgetProvider.class.getSimpleName();
    
    @Override
    public void onReceive(Context context, Intent intent) {
        MyLog.i(TAG, "onReceive; action=" + intent.getAction());
        myContextHolder.initialize(context, TAG).getBlocking();
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        MyLog.v(TAG, () -> "onUpdate; ids=" + Arrays.toString(appWidgetIds));
        AppWidgets.of(NotificationEvents.newInstance()).updateViews(appWidgetManager, filterIds(appWidgetIds));
    }

    private static Predicate<MyAppWidgetData> filterIds(int[] appWidgetIds) {
        return data -> Arrays.stream(appWidgetIds).boxed().anyMatch(id -> data.getId() == id);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        MyLog.v(TAG, () -> "onDeleted; ids=" + Arrays.toString(appWidgetIds));
        AppWidgets.of(NotificationEvents.newInstance()).list()
                .stream().filter(filterIds(appWidgetIds)).forEach(MyAppWidgetData::update);
    }

    @Override
    public void onEnabled(Context context) {
        MyLog.v(TAG, "onEnabled");
    }

    @Override
    public void onDisabled(Context context) {
        MyLog.v(TAG, "onDisabled");
    }
}
