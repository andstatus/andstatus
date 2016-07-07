package org.andstatus.app.appwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.MyLog;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppWidgets {
    private MyContext myContext;
    private final Map<Integer, MyAppWidgetData> mAppWidgets = new ConcurrentHashMap<Integer, MyAppWidgetData>();

    public static void clearAndUpdateWidgets(MyContext myContext) {
        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        appWidgets.clearCounters();
        appWidgets.updateViews();
    }

    public static void updateWidgets(MyContext myContext) {
        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        appWidgets.updateViews();
    }

    public static AppWidgets newInstance(MyContext myContext) {
        return new AppWidgets(myContext);
    }

    private AppWidgets(MyContext myContext) {
        this.myContext = myContext;
        for (int id : getAppWidgetIds(myContext.context())) {
            mAppWidgets.put(id, MyAppWidgetData.newInstance(myContext.context(), id));
        }
    }

    private int[] getAppWidgetIds(Context context) {
        return AppWidgetManager
                .getInstance(context)
                .getAppWidgetIds(
                        new ComponentName(context, MyAppWidgetProvider.class));
    }

    public void updateData(CommandResult result) {
        for (MyAppWidgetData widgetData : mAppWidgets.values()) {
            widgetData.update(result);
        }
    }

    public void clearCounters() {
        for (MyAppWidgetData widgetData : mAppWidgets.values()) {
            widgetData.clearCounters();
            widgetData.save();
        }
    }

    public Collection<MyAppWidgetData> collection() {
        return mAppWidgets.values();
    }

    public boolean isEmpty() {
        return mAppWidgets.isEmpty();
    }

    public int size() {
        return mAppWidgets.size();
    }

    public void updateViews(){
        MyLog.v(this, "Sending update to " +  size() + " remote view" + (size() > 1 ? "s" : "") +
        " " + mAppWidgets.values());
        for (MyAppWidgetData widgetData : mAppWidgets.values()) {
            updateView(AppWidgetManager.getInstance(myContext.context()), widgetData);
        }
    }

    void updateView(AppWidgetManager appWidgetManager,
            int appWidgetId) {
        final String method = "updateView";
        MyAppWidgetData widgetData = mAppWidgets.get(appWidgetId);
        if (widgetData == null) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.d(this, method + "; Widget not found, id=" + appWidgetId);
            }
            return;
        }
        updateView(appWidgetManager, widgetData);
    }

    void updateView(AppWidgetManager appWidgetManager,
            MyAppWidgetData widgetData) {
        final String method = "updateView";
        try {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + "; Started id=" + widgetData.getId());
            }
            MyRemoteViewData viewData = MyRemoteViewData.fromViewData(myContext.context(), widgetData);
            RemoteViews views = constructRemoteViews(myContext.context(), viewData);
            appWidgetManager.updateAppWidget(widgetData.getId(), views);
        } catch (Exception e) {
            MyLog.e(this, method, e);
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

}
