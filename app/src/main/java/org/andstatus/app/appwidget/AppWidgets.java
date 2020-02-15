package org.andstatus.app.appwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AppWidgets {
    final NotificationEvents events;
    private final List<MyAppWidgetData> list;

    public static AppWidgets of(MyContext myContext) {
        return of(myContext.getNotifier().getEvents());
    }

    public static AppWidgets of(NotificationEvents events) {
        return new AppWidgets(events);
    }

    private AppWidgets(NotificationEvents events) {
        this.events = events;
        list = events.myContext == null
            ? Collections.emptyList()
            : Arrays.stream(
                AppWidgetManager.getInstance(events.myContext.context())
                        .getAppWidgetIds(new ComponentName(events.myContext.context(), MyAppWidgetProvider.class)))
                .boxed()
                .map(id -> MyAppWidgetData.newInstance(events, id))
                .collect(Collectors.toList());
    }

    public AppWidgets updateData() {
        list.forEach(MyAppWidgetData::update);
        return this;
    }

    public AppWidgets clearCounters() {
        list.forEach(data -> {
            data.clearCounters();
            data.save();
        });
        return this;
    }

    public List<MyAppWidgetData> list() {
        return list;
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public int size() {
        return list.size();
    }

    public void updateViews(){
        updateViews(AppWidgetManager.getInstance(events.myContext.context()), data -> true);
    }

    public void updateViews(AppWidgetManager appWidgetManager, Predicate<MyAppWidgetData> predicate){
        MyLog.v(this, () -> "Sending update to " +  size() + " remote view" + (size() > 1 ? "s" : "")
                + " " + list);
        list.stream().filter(predicate).forEach(data -> updateView(appWidgetManager, data));
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
        if (StringUtil.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.GONE);
        }
        if (StringUtil.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment, android.view.View.GONE);
        }
        if (!StringUtil.isEmpty(viewData.widgetText)) {
            views.setViewVisibility(R.id.appwidget_text, android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_text, viewData.widgetText);
        }
        if (!StringUtil.isEmpty(viewData.widgetComment)) {
            views.setViewVisibility(R.id.appwidget_comment,
                    android.view.View.VISIBLE);
            views.setTextViewText(R.id.appwidget_comment, viewData.widgetComment);
        }
        views.setTextViewText(R.id.appwidget_time, viewData.widgetTime);
        views.setOnClickPendingIntent(R.id.widget, viewData.onClickIntent);
        return views;
    }

}
