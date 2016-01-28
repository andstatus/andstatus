package org.andstatus.app.appwidget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.msg.TimelineTypeSelector;
import org.andstatus.app.util.I18n;
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
                        + I18n.formatQuantityMessage(context,
                                R.string.appwidget_new_mention_format,
                                widgetData.numMentions,
                                R.array.appwidget_mention_patterns,
                                R.array.appwidget_mention_formats);
            }
            if (widgetData.numDirectMessages > 0) {
                isFound = true;
                widgetText += (widgetText.length() > 0 ? "\n" : "")
                        + I18n.formatQuantityMessage(context,
                                R.string.appwidget_new_directmessage_format,
                                widgetData.numDirectMessages,
                                R.array.appwidget_directmessage_patterns,
                                R.array.appwidget_directmessage_formats);
            }
            if (widgetData.numHomeTimeline > 0) {
                isFound = true;
                widgetText += (widgetText.length() > 0 ? "\n" : "")
                        + I18n.formatQuantityMessage(context,
                                R.string.appwidget_new_home_format,
                                widgetData.numHomeTimeline, R.array.appwidget_home_patterns,
                                R.array.appwidget_home_formats);
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
        if (widgetData.numDirectMessages > 0) {
            timeLineType = TimelineType.DIRECT;
        } else if (widgetData.numMentions > 0) {
                timeLineType = TimelineType.MENTIONS;
        } else if (widgetData.numHomeTimeline > 0) {
            timeLineType = TimelineType.HOME;
        }
        boolean isTimelineCombined;
        if (timeLineType == TimelineType.UNKNOWN) {
            timeLineType = TimelineTypeSelector.getDefault();
            isTimelineCombined = MyPreferences.getBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false);
        } else {
            // There are more than one account,
            // so turn Combined timeline on in order to show all the new messages.
            isTimelineCombined = MyContextHolder.get().persistentAccounts().size() > 1;
        }

        // TODO: We don't mention exact MyAccount in the intent 
        // On the other hand the Widget is not Account aware yet also,
        //   so for now this is correct.
        long myAccountId = MyContextHolder.get().persistentAccounts().getCurrentAccountUserId();

        Intent intent = new Intent(context, TimelineActivity.class);
        // "rnd" is necessary to actually bring Extra to the target intent
        // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
        intent.setData(Uri.withAppendedPath(MatchedUri.getTimelineUri(
                myAccountId, timeLineType, isTimelineCombined, 0),
                "rnd/" + android.os.SystemClock.elapsedRealtime()));
        return PendingIntent.getActivity(context, timeLineType.hashCode(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}