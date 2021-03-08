package org.andstatus.app.appwidget

import android.app.PendingIntent
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Time
import org.andstatus.app.R
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime

internal class MyRemoteViewData {
    /** "Text" is what is shown in bold  */
    var widgetText: String? = ""

    /** And the "Comment" is less visible, below the "Text"  */
    var widgetComment: String? = ""

    /** Construct period of counting...  */
    var widgetTime: String? = ""
    var onClickIntent: PendingIntent? = null
    private fun getViewData(context: Context?, widgetData: MyAppWidgetData?) {
        val method = "updateView"
        if (widgetData.dateLastChecked == 0L) {
            MyLog.d(this, "dateLastChecked==0 ?? " + widgetData.toString())
            widgetComment = context.getString(R.string.appwidget_nodata)
        } else {
            widgetTime = formatWidgetTime(context, widgetData.dateSince, widgetData.dateLastChecked)
            widgetData.events.map.values.stream().filter { detail: NotificationData? -> detail.getCount() > 0 }
                    .forEach { detail: NotificationData? ->
                        widgetText += ((if (widgetText.length > 0) "\n" else "")
                                + context.getText(detail.event.titleResId) + ": " + detail.getCount())
                    }
            if (widgetData.events.isEmpty) {
                widgetComment = widgetData.nothingPref
            }
        }
        onClickIntent = widgetData.events.pendingIntent
        MyLog.v(this) {
            (method + "; text=\"" + widgetText.replace("\n".toRegex(), "; ")
                    + "\"; comment=\"" + widgetComment + "\"")
        }
    }

    companion object {
        fun fromViewData(context: Context?, widgetData: MyAppWidgetData?): MyRemoteViewData {
            val viewData = MyRemoteViewData()
            viewData.getViewData(context, widgetData)
            return viewData
        }

        fun formatWidgetTime(context: Context?, startMillis: Long,
                             endMillis: Long): String? {
            var formatted: String? = ""
            var strStart: String? = ""
            var strEnd: String? = ""
            if (endMillis == 0L) {
                formatted = "=0 ???"
                MyLog.w(context, "formatWidgetTime: endMillis == 0")
            } else {
                val timeStart = Time()
                timeStart.set(startMillis)
                val timeEnd = Time()
                timeEnd.set(endMillis)
                var flags = 0
                if (timeStart.yearDay < timeEnd.yearDay) {
                    strStart = RelativeTime.getDifference(context, startMillis)
                    if (DateUtils.isToday(endMillis)) {
                        // End - today
                        flags = DateUtils.FORMAT_SHOW_TIME
                        if (DateFormat.is24HourFormat(context)) {
                            flags = flags or DateUtils.FORMAT_24HOUR
                        }
                        strEnd = DateUtils.formatDateTime(context, endMillis, flags)
                    } else {
                        strEnd = RelativeTime.getDifference(context, endMillis)
                    }
                } else {
                    // Same day
                    if (DateUtils.isToday(endMillis)) {
                        // Start and end - today
                        flags = DateUtils.FORMAT_SHOW_TIME
                        if (DateFormat.is24HourFormat(context)) {
                            flags = flags or DateUtils.FORMAT_24HOUR
                        }
                        strStart = DateUtils.formatDateTime(context, startMillis, flags)
                        strEnd = DateUtils.formatDateTime(context, endMillis, flags)
                    } else {
                        strStart = RelativeTime.getDifference(context, endMillis)
                    }
                }
                formatted = strStart
                if (strEnd.length > 0
                        && strEnd.compareTo(strStart) != 0) {
                    if (formatted.length > 0) {
                        formatted += " - "
                    }
                    formatted += strEnd
                }
            }
            return formatted
        }
    }
}