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
package com.xorcode.andtweet.appwidget;

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

import com.xorcode.andtweet.MessageListActivity;
import com.xorcode.andtweet.R;
import com.xorcode.andtweet.AndTweetService;
import com.xorcode.andtweet.TweetListActivity;
import com.xorcode.andtweet.data.AndTweetDatabase;
import com.xorcode.andtweet.util.I18n;
import com.xorcode.andtweet.util.MyLog;
import com.xorcode.andtweet.util.RelativeTime;

/**
 * A widget provider. If uses AndTweetAppWidgetData to store preferences and to
 * accumulate data (notifications...) received.
 * 
 * <p>
 * See also the following files:
 * <ul>
 * <li>AndTweetAppWidgetData.java</li>
 * <li>AndTweetAppWidgetConfigure.java</li>
 * <li>res/layout/appwidget_configure.xml</li>
 * <li>res/layout/appwidget.xml</li>
 * <li>res/xml/appwidget_info.xml</li>
 * </ul>
 * 
 * @author yvolk (Yuri Volkov), http://yurivolkov.com
 */
public class AndTweetAppWidgetProvider extends AppWidgetProvider {
	// log tag
	private static final String TAG = AndTweetAppWidgetProvider.class
			.getSimpleName();

	private AndTweetService.CommandEnum msgType = AndTweetService.CommandEnum.UNKNOWN;
	private int numSomethingReceived = 0;
	private static Object xlock = new Object();
	private long instanceId = Math.abs(new java.util.Random().nextInt());

	@Override
	public void onReceive(Context context, Intent intent) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onReceive; intent=" + intent);
        }
		boolean done = false;
		String action = intent.getAction();

		if (AndTweetService.ACTION_APPWIDGET_UPDATE.equals(action)) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "inst=" + instanceId + "; Intent from AndTweetService received!");
            }
			Bundle extras = intent.getExtras();
			if (extras != null) {
				msgType = AndTweetService.CommandEnum.load(extras.getString(AndTweetService.EXTRA_MSGTYPE));
				numSomethingReceived = extras
						.getInt(AndTweetService.EXTRA_NUMTWEETS);
				int[] appWidgetIds = extras
						.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
				if (appWidgetIds == null || appWidgetIds.length == 0) {
					/**
					 * Update All AndTweet AppWidgets
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
                Log.v(TAG, "inst=" + instanceId + "; Intent from AndTweetService processed");
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
		// - Tell the AppWidgetManager to show that views object for the widget.
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
			new AndTweetAppWidgetData(context, appWidgetIds[i]).delete();
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
                Log.v(TAG, "inst=" + instanceId + "; updateAppWidget appWidgetId=" + appWidgetId 
                        + "; msgType=" + msgType);
            }

			// TODO: Do we need AlarmManager here?
			// see /ApiDemos/src/com/example/android/apis/app/AlarmController.java
			// on how to implement AlarmManager...

			AndTweetAppWidgetData data;		
			synchronized(xlock) {
				data = new AndTweetAppWidgetData(context,
						appWidgetId);
				data.load();

		        if (AndTweetService.updateWidgetsOnEveryUpdate || (numSomethingReceived != 0)) {
                    data.changed = true;
		        }
				// Calculate new values
				switch (msgType) {
				case NOTIFY_REPLIES:
					data.numMentions += numSomethingReceived;
					data.checked();
					break;
		
				case NOTIFY_DIRECT_MESSAGE:
					data.numMessages += numSomethingReceived;
                    data.checked();
					break;
		
				case NOTIFY_TIMELINE:
					data.numTweets += numSomethingReceived;
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
	            if (data.numMessages > 0) {
	                isFound = true;
	                widgetText += (widgetText.length() > 0 ? "\n" : "")
	                        + I18n.formatQuantityMessage(context,
	                                R.string.appwidget_new_message_format,
	                                data.numMessages,
	                                R.array.appwidget_message_patterns,
	                                R.array.appwidget_message_formats);
	            }
	            if (data.numTweets > 0) {
	                isFound = true;
	                widgetText += (widgetText.length() > 0 ? "\n" : "")
	                        + I18n.formatQuantityMessage(context,
	                                R.string.appwidget_new_tweet_format,
	                                data.numTweets, R.array.appwidget_tweet_patterns,
	                                R.array.appwidget_tweet_formats);
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

			// When user clicks on widget, launch main AndTweet activity,
			//   Open timeline, where there are new tweets, or "Friends" timeline
			Intent intent;
			int timeLineType = AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS;
			if (data.numMessages > 0) {
	            intent = new Intent(context, MessageListActivity.class);
			    timeLineType = AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES;
			} else {
	            intent = new Intent(context, TweetListActivity.class);
			    if (data.numMentions > 0) {
	                timeLineType = AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS;
			    }
			}
            intent.putExtra(AndTweetService.EXTRA_TIMELINE_TYPE,
                    timeLineType);
            // This line is necessary to actually bring Extra to the target intent
            // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
            intent.setData((android.net.Uri.parse(AndTweetDatabase.Tweets.CONTENT_URI.toString() + "#" + android.os.SystemClock.elapsedRealtime())));
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
			Log.e(TAG, "inst=" + instanceId + "; updateAppWidget exception: " + e.toString() );
			
		} finally {
            if ( !Ok || MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "inst=" + instanceId + "; updateAppWidget " + (Ok ? "succeded" : "failed") );
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
