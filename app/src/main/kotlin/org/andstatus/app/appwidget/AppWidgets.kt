package org.andstatus.app.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog

class AppWidgets private constructor(val events: NotificationEvents): IsEmpty {
    val listOfData: List<MyAppWidgetData> =
        if (events === NotificationEvents.EMPTY) emptyList()
        else events.myContext.appWidgetIds
            .map { id: Int -> MyAppWidgetData.newInstance(events, id) }

    fun updateData(): AppWidgets {
        listOfData.forEach { it.update() }
        return this
    }

    fun clearCounters(): AppWidgets {
        listOfData.forEach {
            it.clearCounters()
            it.save()
        }
        return this
    }

    override val isEmpty: Boolean = listOfData.isEmpty()
    val size: Int get() = listOfData.size

    fun updateViews(
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(events.myContext.context),
        predicate: (MyAppWidgetData) -> Boolean = { _ -> true }
    ) {
        MyLog.v(this) {
            "Sending update to " + size + " remote view" + (if (size > 1) "s" else "") + " " + listOfData
        }
        listOfData.filter(predicate).forEach { data: MyAppWidgetData -> updateView(appWidgetManager, data) }
    }

    private fun updateView(appWidgetManager: AppWidgetManager, widgetData: MyAppWidgetData) {
        val method = "updateView"
        try {
            MyLog.v(this) { method + "; Started id=" + widgetData.appWidgetId }
            val viewData: MyRemoteViewData = MyRemoteViewData.fromViewData(events.myContext.context, widgetData)
            val views = constructRemoteViews(events.myContext.context, viewData)
            appWidgetManager.updateAppWidget(widgetData.appWidgetId, views)
        } catch (e: Exception) {
            MyLog.i(this, method, e)
        }
    }

    /**  Construct the RemoteViews object. It takes the package name (in our case, it's our
     * package, but it needs this because on the other side it's the widget
     * host inflating the layout from our package).   */
    private fun constructRemoteViews(context: Context, viewData: MyRemoteViewData): RemoteViews {
        val views = RemoteViews(context.getPackageName(), R.layout.appwidget)
        if (viewData.widgetText.isEmpty()) {
            views.setViewVisibility(R.id.appwidget_text, View.GONE)
        }
        if (viewData.widgetComment.isEmpty()) {
            views.setViewVisibility(R.id.appwidget_comment, View.GONE)
        }
        if (!viewData.widgetText.isEmpty()) {
            views.setViewVisibility(R.id.appwidget_text, View.VISIBLE)
            views.setTextViewText(R.id.appwidget_text, viewData.widgetText)
        }
        if (!viewData.widgetComment.isEmpty()) {
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
            return of(myContext.notifier.getEvents())
        }

        fun of(events: NotificationEvents): AppWidgets {
            return AppWidgets(events)
        }
    }
}
