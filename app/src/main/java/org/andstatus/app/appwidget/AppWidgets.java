package org.andstatus.app.appwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import org.andstatus.app.R;
import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppWidgets {
    final NotificationEvents events;
    private final Map<Integer, MyAppWidgetData> widgetsMap = new ConcurrentHashMap<>();

    public static void clearAndUpdateWidgets(NotificationEvents events) {
        new AppWidgets(events).clearCounters().updateViews();
    }

    public static void updateWidgets(NotificationEvents events) {
        new AppWidgets(events).updateViews();
    }

    public AppWidgets(NotificationEvents events) {
        this.events = events;
        for (int id : getIds(events.myContext.context())) {
            widgetsMap.put(id, MyAppWidgetData.newInstance(events, id));
        }
    }

    private int[] getIds(Context context) {
        return AppWidgetManager
                .getInstance(context)
                .getAppWidgetIds(new ComponentName(context, MyAppWidgetProvider.class));
    }

    public AppWidgets updateData() {
        widgetsMap.values().forEach(MyAppWidgetData::update);
        return this;
    }

    private AppWidgets clearCounters() {
        for (MyAppWidgetData widgetData : widgetsMap.values()) {
            widgetData.clearCounters();
            widgetData.save();
        }
        return this;
    }

    public Collection<MyAppWidgetData> collection() {
        return widgetsMap.values();
    }

    public boolean isEmpty() {
        return widgetsMap.isEmpty();
    }

    public int size() {
        return widgetsMap.size();
    }

    public void updateViews(){
        MyLog.v(this, () -> "Sending update to " +  size() + " remote view" + (size() > 1 ? "s" : "")
                + " " + widgetsMap.values());
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(events.myContext.context());
        widgetsMap.values().forEach(data -> updateView(appWidgetManager, data));
    }

    void updateView(AppWidgetManager appWidgetManager, int appWidgetId) {
        final String method = "updateView";
        MyAppWidgetData widgetData = widgetsMap.get(appWidgetId);
        if (widgetData == null) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.d(this, method + "; Widget not found, id=" + appWidgetId);
            }
            return;
        }
        updateView(appWidgetManager, widgetData);
    }

    private void updateView(AppWidgetManager appWidgetManager, MyAppWidgetData widgetData) {
        final String method = "updateView";
        try {
            MyLog.v(this, () -> method + "; Started id=" + widgetData.getId());
            MyRemoteViewData viewData = MyRemoteViewData.fromViewData(events.myContext.context(), widgetData);
            RemoteViews views = constructRemoteViews(events.myContext.context(), viewData);
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
        if (StringUtils.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.GONE);
        }
        if (StringUtils.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment, android.view.View.GONE);
        }
        if (!StringUtils.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_text, viewData.widgetText);
        }
        if (!StringUtils.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment,
                    android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_comment, viewData.widgetComment);
        }
        views.setTextViewText(R.id.appwidget_time, viewData.widgetTime);
        views.setOnClickPendingIntent(R.id.widget, viewData.onClickIntent);
        return views;
    }

}
