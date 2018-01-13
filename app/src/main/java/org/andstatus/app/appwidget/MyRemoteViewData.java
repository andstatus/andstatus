package org.andstatus.app.appwidget;

import android.app.PendingIntent;
import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

class MyRemoteViewData {
    /** "Text" is what is shown in bold */
    String widgetText = "";
    /** And the "Comment" is less visible, below the "Text" */
    String widgetComment = "";
    /** Construct period of counting... */
    String widgetTime = "";
    PendingIntent onClickIntent = null;

    static MyRemoteViewData fromViewData(Context context, MyAppWidgetData widgetData) {
        MyRemoteViewData viewData = new MyRemoteViewData();
        viewData.getViewData(context, widgetData);
        return viewData;
    }
    
    private void getViewData(Context context, MyAppWidgetData widgetData) {
        final String method = "updateView";
        if (widgetData.dateLastChecked == 0) {
            MyLog.d(this, "dateLastChecked==0 ?? " + widgetData.toString());
            widgetComment = context.getString(R.string.appwidget_nodata);
        } else {
            widgetTime = formatWidgetTime(context, widgetData.dateSince, widgetData.dateLastChecked);
            widgetData.notifier.events.map.values().stream().filter(detail -> detail.getCount() > 0)
                    .forEach(detail ->
                            widgetText += (widgetText.length() > 0 ? "\n" : "")
                            + context.getText(detail.event.titleResId) + ": " + detail.getCount());
            if (widgetData.notifier.events.isEmpty()) {
                widgetComment = widgetData.nothingPref;
            }
        }
        onClickIntent = widgetData.notifier.events.getPendingIntent();
        
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; text=\"" + widgetText.replaceAll("\n", "; ") + "\"; comment=\""
                    + widgetComment + "\"");
        }
    }
    
    static String formatWidgetTime(Context context, long startMillis,
            long endMillis) {
        String formatted = "";
        String strStart = "";
        String strEnd = "";

        if (endMillis == 0) {
            formatted = "=0 ???";
            MyLog.e(context, "data.dateUpdated==0");
        } else {
            Time timeStart = new Time();
            timeStart.set(startMillis);
            Time timeEnd = new Time();
            timeEnd.set(endMillis);
            int flags = 0;

            if (timeStart.yearDay < timeEnd.yearDay) {
                strStart = RelativeTime.getDifference(context, startMillis);
                if (DateUtils.isToday(endMillis)) {
                    // End - today
                    flags = DateUtils.FORMAT_SHOW_TIME;
                    if (DateFormat.is24HourFormat(context)) {
                        flags |= DateUtils.FORMAT_24HOUR;
                    }
                    strEnd = DateUtils.formatDateTime(context, endMillis, flags);
                } else {
                    strEnd = RelativeTime.getDifference(context, endMillis);
                }
            } else {
                // Same day
                if (DateUtils.isToday(endMillis)) {
                    // Start and end - today
                    flags = DateUtils.FORMAT_SHOW_TIME;
                    if (DateFormat.is24HourFormat(context)) {
                        flags |= DateUtils.FORMAT_24HOUR;
                    }
                    strStart = DateUtils.formatDateTime(context, startMillis, flags);
                    strEnd = DateUtils.formatDateTime(context, endMillis, flags);

                } else {
                    strStart = RelativeTime.getDifference(context, endMillis);
                }
            }
            formatted = strStart;
            if (strEnd.length() > 0
                    && strEnd.compareTo(strStart) != 0) {
                if (formatted.length() > 0) {
                    formatted += " - ";
                }
                formatted += strEnd;
            }
        }       
        return formatted;
    }
}