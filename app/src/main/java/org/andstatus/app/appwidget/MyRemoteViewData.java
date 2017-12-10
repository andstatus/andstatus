package org.andstatus.app.appwidget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.notification.NotificationEvent;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
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
            boolean isFound = false;

            if (widgetData.numMentions > 0) {
                isFound = true;
                widgetText += (widgetText.length() > 0 ? "\n" : "")
                    + context.getText(NotificationEvent.MENTION.titleResId) + ": " + widgetData.numMentions;
            }
            if (widgetData.numPrivate > 0) {
                isFound = true;
                widgetText += (widgetText.length() > 0 ? "\n" : "")
                    + context.getText(NotificationEvent.PRIVATE.titleResId) + ": " + widgetData.numPrivate;
            }
            if (widgetData.numReblogs > 0) {
                isFound = true;
                widgetText += (widgetText.length() > 0 ? "\n" : "")
                    + context.getText(NotificationEvent.ANNOUNCE.titleResId) + ": " + widgetData.numReblogs;
            }
            if (!isFound) {
                widgetComment = widgetData.nothingPref;
            }
        }
        onClickIntent = getOnClickIntent(context, widgetData);
        
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; text=\"" + widgetText.replaceAll("\n", "; ") + "\"; comment=\""
                    + widgetComment + "\"");
        }
    }
    
    public static String formatWidgetTime(Context context, long startMillis,
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
    
    /** When user clicks on a widget, launch main AndStatus activity,
     *  Open the timeline, which has new messages, or default to the "Home" timeline
     */
    private PendingIntent getOnClickIntent(Context context, MyAppWidgetData widgetData) {
        TimelineType timeLineType = TimelineType.UNKNOWN;
        if (widgetData.numPrivate > 0) {
            timeLineType = TimelineType.DIRECT;
        } else if (widgetData.numMentions > 0) {
                timeLineType = TimelineType.MENTIONS;
        } else if (widgetData.numReblogs > 0) {
            timeLineType = TimelineType.NOTIFICATIONS;
        }

        // TODO: MyAccount in the intent is not necessarily the one, which got new messages
        // But so far this looks better than Combined timeline for most users...
        Timeline timeline;
        switch(timeLineType) {
            case UNKNOWN:
                timeline = MyContextHolder.get().persistentTimelines().getDefault();
                break;
            default:
                timeline = Timeline.getTimeline(timeLineType,
                        MyContextHolder.get().persistentAccounts().getCurrentAccount(), 0, null);
                break;
        }

        Intent intent = new Intent(context, FirstActivity.class);
        // "rnd" is necessary to actually bring Extra to the target intent
        // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
        intent.setData(Uri.withAppendedPath(timeline.getUri(),
                "rnd/" + android.os.SystemClock.elapsedRealtime()));
        return PendingIntent.getActivity(context, timeLineType.hashCode(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}