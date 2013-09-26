/*
 * Copyright (C) 2010-2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.text.format.DateFormat; 
import android.text.format.DateUtils;
import android.text.format.Time;

import android.util.Log;
import android.widget.RemoteViews;

import org.andstatus.app.MyService;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * A widget provider. If uses MyAppWidgetData to store preferences and to
 * accumulate data (notifications...) received.
 * 
 * <p>
 * See also the following files:
 * <ul>
 * <li>MyAppWidgetData.java</li>
 * <li>MyAppWidgetConfigure.java</li>
 * <li>res/layout/appwidget_configure.xml</li>
 * <li>res/layout/appwidget.xml</li>
 * <li>res/xml/appwidget_info.xml</li>
 * </ul>
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProvider extends AppWidgetProvider {
	// log tag
	private static final String TAG = MyAppWidgetProvider.class
			.getSimpleName();

	private MyService.CommandEnum msgType = MyService.CommandEnum.UNKNOWN;
	private int numSomethingReceived = 0;
	private static Object xlock = new Object();
	private final int instanceId = MyPreferences.nextInstanceId();

	@Override
	public void onReceive(Context context, Intent intent) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onReceive; intent=" + intent);
        }
		boolean done = false;
		String action = intent.getAction();

		if (MyService.ACTION_APPWIDGET_UPDATE.equals(action)) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "instanceId=" + instanceId + "; Intent from MyService received!");
            }
			Bundle extras = intent.getExtras();
			if (extras != null) {
				msgType = MyService.CommandEnum.load(extras.getString(MyService.EXTRA_MSGTYPE));
				numSomethingReceived = extras
						.getInt(MyService.EXTRA_NUMTWEETS);
				int[] appWidgetIds = extras
						.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
				if (appWidgetIds == null || appWidgetIds.length == 0) {
					/**
					 * Update All AndStatus AppWidgets
					 * */
					appWidgetIds = AppWidgetManager
							.getInstance(context)
							.getAppWidgetIds(
									new ComponentName(context, this.getClass()));
				}
				if (appWidgetIds != null && appWidgetIds.length > 0) {
					onUpdate(context, AppWidgetManager.getInstance(context),
							appWidgetIds);
					done = true;
				}
			}
			if (!done) {
				// This will effectively reset the Widget view
				updateAppWidget(context, AppWidgetManager.getInstance(context),
						AppWidgetManager.INVALID_APPWIDGET_ID);
				done = true;
			}
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "instanceId=" + instanceId + "; Intent from MyService processed");
            }
		} else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Action APPWIDGET_DELETED was received");
            }
			Bundle extras = intent.getExtras();
			if (extras != null) {
				int[] appWidgetIds = extras
						.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
				if (appWidgetIds != null && appWidgetIds.length > 0) {
					onDeleted(context, appWidgetIds);
					done = true;
				} else {
					// For some reason this is required for Android v.1.5
					int appWidgetId = extras
							.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
					if (appWidgetId != 0) {
						int[] appWidgetIds2 = { appWidgetId };
						onDeleted(context, appWidgetIds2);
						done = true;
					}
				}
			}
			if (!done) {
	            if (MyLog.isLoggable(TAG, Log.DEBUG)) {
	                Log.d(TAG, "Deletion was not done, extras='"
	                        + extras.toString() + "'");
	            }
			}
		}
		if (!done) {
			super.onReceive(context, intent);
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onUpdate");
        }
		// For each widget that needs an update, get the text that we should
		// display:
		// - Create a RemoteViews object for it
		// - Set the text in the RemoteViews object
		// - Tell the AppWidgetManager to show that rows object for the widget.
		final int N = appWidgetIds.length;
		for (int i = 0; i < N; i++) {
			int appWidgetId = appWidgetIds[i];
			updateAppWidget(context, appWidgetManager, appWidgetId);
		}
	}

	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onDeleted");
        }
		// When the user deletes the widget, delete all preferences associated
		// with it.
		final int N = appWidgetIds.length;
		for (int i = 0; i < N; i++) {
			new MyAppWidgetData(context, appWidgetIds[i]).delete();
		}
	}

	@Override
	public void onEnabled(Context context) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onEnabled");
        }
	}

	@Override
	public void onDisabled(Context context) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onDisabled");
        }

	}

	/**
	 * Update the AppWidget view (i.e. on the Home screen)
	 * 
	 * @param context
	 * @param appWidgetManager
	 * @param appWidgetId
	 *            Id of the The AppWidget instance which view should be updated
	 */
	void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
			int appWidgetId) {
		boolean Ok = false;
		try {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "instanceId=" + instanceId + "; updateAppWidget appWidgetId=" + appWidgetId 
                        + "; msgType=" + msgType);
            }

			// TODO: Do we need AlarmManager here?
			// see /ApiDemos/src/com/example/android/apis/app/AlarmController.java
			// on how to implement AlarmManager...

			MyAppWidgetData data;		
			synchronized(xlock) {
				data = new MyAppWidgetData(context,
						appWidgetId);
				data.load();

		        if (MyService.updateWidgetsOnEveryUpdate || (numSomethingReceived != 0)) {
                    data.changed = true;
		        }
				// Calculate new values
				switch (msgType) {
				case NOTIFY_MENTIONS:
					data.numMentions += numSomethingReceived;
					data.checked();
					break;
		
				case NOTIFY_DIRECT_MESSAGE:
					data.numDirectMessages += numSomethingReceived;
                    data.checked();
					break;
		
				case NOTIFY_HOME_TIMELINE:
					data.numHomeTimeline += numSomethingReceived;
                    data.checked();
					break;
		
				case NOTIFY_CLEAR:
					data.clearCounters();
					break;
		
				default:
					// change nothing
				}
				if (data.changed) {
					data.save();
				}
			}

			// TODO: Widget design...

			// "Text" is what is shown in bold
			String widgetText = "";
			// And the "Comment" is less visible, below the "Text"
			String widgetComment = "";

			// Construct period of counting...
			String widgetTime = "";
			if (data.dateChecked == 0) {
                Log.e(TAG, "data.dateChecked==0");
                widgetComment = context.getString(R.string.appwidget_nodata);
			} else {
				widgetTime = formatWidgetTime(context, data.dateCleared, data.dateChecked);
	            boolean isFound = false;

	            if (data.numMentions > 0) {
	                isFound = true;
	                widgetText += (widgetText.length() > 0 ? "\n" : "")
	                        + I18n.formatQuantityMessage(context,
	                                R.string.appwidget_new_mention_format,
	                                data.numMentions,
	                                R.array.appwidget_mention_patterns,
	                                R.array.appwidget_mention_formats);
	            }
	            if (data.numDirectMessages > 0) {
	                isFound = true;
	                widgetText += (widgetText.length() > 0 ? "\n" : "")
	                        + I18n.formatQuantityMessage(context,
	                                R.string.appwidget_new_message_format,
	                                data.numDirectMessages,
	                                R.array.appwidget_directmessage_patterns,
	                                R.array.appwidget_directmessage_formats);
	            }
	            if (data.numHomeTimeline > 0) {
	                isFound = true;
	                widgetText += (widgetText.length() > 0 ? "\n" : "")
	                        + I18n.formatQuantityMessage(context,
	                                R.string.appwidget_new_tweet_format,
	                                data.numHomeTimeline, R.array.appwidget_message_patterns,
	                                R.array.appwidget_message_formats);
	            }
	            if (!isFound) {
	                widgetComment = data.nothingPref;
	            }
			}

            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "updateAppWidget text=\"" + widgetText.replaceAll("\n", "; ") + "\"; comment=\""
                        + widgetComment + "\"");
            }

			// Construct the RemoteViews object. It takes the package name (in our
			// case, it's our
			// package, but it needs this because on the other side it's the widget
			// host inflating
			// the layout from our package).
			RemoteViews views = new RemoteViews(context.getPackageName(),
					R.layout.appwidget);

			if (widgetText.length() == 0) {
				views
						.setViewVisibility(R.id.appwidget_text,
								android.view.View.GONE);
			}
			if (widgetComment.length() == 0) {
				views.setViewVisibility(R.id.appwidget_comment,
						android.view.View.GONE);
			}

			if (widgetText.length() > 0) {
				views.setViewVisibility(R.id.appwidget_text,
						android.view.View.VISIBLE);
				views.setTextViewText(R.id.appwidget_text, widgetText);
			}
			if (widgetComment.length() > 0) {
				views.setViewVisibility(R.id.appwidget_comment,
						android.view.View.VISIBLE);
				views.setTextViewText(R.id.appwidget_comment, widgetComment);
			}
			views.setTextViewText(R.id.appwidget_time, widgetTime);

			// When user clicks on widget, launch main AndStatus activity,
			//   Open timeline, where there are new messages, or "Home" timeline
			Intent intent;
			MyDatabase.TimelineTypeEnum timeLineType = TimelineTypeEnum.HOME;
            intent = new Intent(context, TimelineActivity.class);
			if (data.numDirectMessages > 0) {
			    timeLineType = MyDatabase.TimelineTypeEnum.DIRECT;
			} else {
			    if (data.numMentions > 0) {
	                timeLineType = MyDatabase.TimelineTypeEnum.MENTIONS;
			    }
			}
            intent.putExtra(MyService.EXTRA_TIMELINE_TYPE,
                    timeLineType.save());

            if ( data.areNew() ) {
                // TODO: We don't mention MyAccount in the intent 
                // On the other hand the Widget is also is not Account aware yet,
                //   so for now this is correct.
                if (MyAccount.list().length > 1) {
                    // There are more than one account, 
                    // so turn Combined timeline on in order to show all new messages.
                    intent.putExtra(MyService.EXTRA_TIMELINE_IS_COMBINED, true);
                }
            }
            
            // This line is necessary to actually bring Extra to the target intent
            // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
            intent.setData((android.net.Uri.parse(MyProvider.TIMELINE_URI.toString() + "#" + android.os.SystemClock.elapsedRealtime())));
			PendingIntent pendingIntent = PendingIntent.getActivity(context,
					0 /* no requestCode */, intent, 0 /* no flags */);
			views.setOnClickPendingIntent(R.id.widget, pendingIntent);

			// Tell the widget manager
			if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
				// TODO: Is this right?
				// All instances will be updated
				appWidgetManager.updateAppWidget(new ComponentName(context, this
						.getClass()), views);
			} else {
				appWidgetManager.updateAppWidget(appWidgetId, views);
			}
			Ok = true;
		} catch (Exception e) {
			Log.e(TAG, "instanceId=" + instanceId + "; updateAppWidget exception: " + e.toString() );
			
		} finally {
            if ( !Ok || MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "instanceId=" + instanceId + "; updateAppWidget " + (Ok ? "succeded" : "failed") );
            }
		}
	}

	public String formatWidgetTime(Context context, long startMillis,
            long endMillis) {
		String widgetTime = "";
		String strStart = "";
		String strEnd = "";

		if (endMillis == 0) {
			widgetTime = "=0 ???";
			Log.e(TAG, "data.dateUpdated==0");
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
			widgetTime = strStart;
			if (strEnd.length()>0) {
				if (strEnd.compareTo(strStart) != 0) {
					if (widgetTime.length()>0) {
						widgetTime += " - ";
					}
					widgetTime += strEnd;
				}
			}
		}		
		
		return widgetTime;
	}
}
