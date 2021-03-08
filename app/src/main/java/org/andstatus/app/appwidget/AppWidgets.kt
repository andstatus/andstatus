package org.andstatus.app.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

class AppWidgets private constructor(val events: NotificationEvents) {
    private val list: List<MyAppWidgetData>
    fun updateData(): AppWidgets {
        list.forEach(Consumer { obj: MyAppWidgetData -> obj.update() })
        return this
    }

    fun clearCounters(): AppWidgets {
        list.forEach(Consumer { data: MyAppWidgetData ->
            data.clearCounters()
            data.save()
        })
        return this
    }

    fun list(): List<MyAppWidgetData> {
        return list
    }

    fun isEmpty(): Boolean {
        return list.isEmpty()
    }

    fun size(): Int {
        return list.size
    }

    @JvmOverloads
    fun updateViews(appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(events.myContext.context()),
                    predicate: Predicate<MyAppWidgetData> = Predicate { data: MyAppWidgetData -> true }) {
        MyLog.v(this) {
            ("Sending update to " + size() + " remote view" + (if (size() > 1) "s" else "")
                    + " " + list)
        }
        list.stream().filter(predicate).forEach { data: MyAppWidgetData -> updateView(appWidgetManager, data) }
    }

    private fun updateView(appWidgetManager: AppWidgetManager, widgetData: MyAppWidgetData) {
        val method = "updateView"
        try {
            MyLog.v(this) { method + "; Started id=" + widgetData.getId() }
            val viewData: MyRemoteViewData = MyRemoteViewData.Companion.fromViewData(events.myContext.context(), widgetData)
            val views = constructRemoteViews(events.myContext.context(), viewData)
            appWidgetManager.updateAppWidget(widgetData.getId(), views)
        } catch (e: Exception) {
            MyLog.i(this, method, e)
        }
    }

    /**  Construct the RemoteViews object. It takes the package name (in our case, it's our
     * package, but it needs this because on the other side it's the widget
     * host inflating the layout from our package).   */
    private fun constructRemoteViews(context: Context, viewData: MyRemoteViewData): RemoteViews {
        val views = RemoteViews(context.getPackageName(),
                R.layout.appwidget)
        if (viewData.widgetText.isNullOrEmpty()) {
            views.setViewVisibility(R.id.appwidget_text, View.GONE)
        }
        if (viewData.widgetComment.isNullOrEmpty()) {
            views.setViewVisibility(R.id.appwidget_comment, View.GONE)
        }
        if (!viewData.widgetText.isNullOrEmpty()) {
            views.setViewVisibility(R.id.appwidget_text, View.VISIBLE)
            views.setTextViewText(R.id.appwidget_text, viewData.widgetText)
        }
        if (!viewData.widgetComment.isNullOrEmpty()) {
            views.setViewVisibility(R.id.appwidget_comment,
                    View.VISIBLE)
            views.setTextViewText(R.id.appwidget_comment, viewData.widgetComment)
        }
        views.setTextViewText(R.id.appwidget_time, viewData.widgetTime)
        views.setOnClickPendingIntent(R.id.widget, viewData.onClickIntent)
        return views
    }

    companion object {
        fun of(myContext: MyContext): AppWidgets {
            return of(myContext.getNotifier().getEvents())
        }

        fun of(events: NotificationEvents): AppWidgets {
            return AppWidgets(events)
        }
    }

    init {
        list = if (events === NotificationEvents.EMPTY) emptyList() else Arrays.stream(
                AppWidgetManager.getInstance(events.myContext.context())
                        .getAppWidgetIds(ComponentName(events.myContext.context(), MyAppWidgetProvider::class.java)))
                .boxed()
                .map { id: Int -> MyAppWidgetData.Companion.newInstance(events, id) }
                .collect(Collectors.toList())
    }
}