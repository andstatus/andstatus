package com.xorcode.andtweet.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.Calendar; 
import android.text.format.DateFormat; 
import android.text.format.DateUtils;
import android.text.format.Time;

import android.util.Log;
import android.widget.RemoteViews;

import com.xorcode.andtweet.R;
import com.xorcode.andtweet.AndTweetService;
import com.xorcode.andtweet.util.RelativeTime;

import static com.xorcode.andtweet.AndTweetService.*;

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

	private int msgType = AndTweetService.NOTIFY_INVALID;
	private int numSomethingReceived = 0;
	private static Object xlock = new Object();
	private long instanceId = Math.abs(new java.util.Random().nextInt());

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "onReceive; intent=" + intent);
		boolean done = false;
		String action = intent.getAction();

		if (AndTweetService.ACTION_APPWIDGET_UPDATE.equals(action)) {
			Log.d(TAG, "inst=" + instanceId + "; Intent from AndTweetService received!");
			Bundle extras = intent.getExtras();
			if (extras != null) {
				msgType = extras.getInt(AndTweetService.EXTRA_MSGTYPE);
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
			Log.d(TAG, "inst=" + instanceId + "; Intent from AndTweetService processed");
		} else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
			Log.d(TAG, "Action APPWIDGET_DELETED was received");
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
				Log.d(TAG, "Deletion was not done, extras='"
						+ extras.toString() + "'");
			}
		}
		if (!done) {
			super.onReceive(context, intent);
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		Log.d(TAG, "onUpdate");
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
		Log.d(TAG, "onDeleted");
		// When the user deletes the widget, delete all preferences associated
		// with it.
		final int N = appWidgetIds.length;
		for (int i = 0; i < N; i++) {
			new AndTweetAppWidgetData(context, appWidgetIds[i]).delete();
		}
	}

	@Override
	public void onEnabled(Context context) {
		Log.d(TAG, "onEnabled");
	}

	@Override
	public void onDisabled(Context context) {
		Log.d(TAG, "onDisabled");

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
			Log.d(TAG, "inst=" + instanceId + "; updateAppWidget appWidgetId=" + appWidgetId 
					+ "; msgType=" + msgType);

			// TODO:
			// see /ApiDemos/src/com/example/android/apis/app/AlarmController.java
			// on how to implement AlarmManager...

			AndTweetAppWidgetData data;		
			synchronized(xlock) {
				data = new AndTweetAppWidgetData(context,
						appWidgetId);
				data.load();
		
				if (numSomethingReceived != 0) {
					data.changed = true;
				}
				// Calculate new values
				switch (msgType) {
				case NOTIFY_REPLIES:
					data.numMentions += numSomethingReceived;
					break;
		
				case NOTIFY_DIRECT_MESSAGE:
					data.numMessages += numSomethingReceived;
					break;
		
				case NOTIFY_TIMELINE:
					data.numTweets += numSomethingReceived;
					break;
		
				case NOTIFY_CLEAR:
					data.clear();
					break;
		
				default:
					// change nothing
				}
				if (data.changed) {
					data.save();
				}
			}

			// TODO: Widget design...

			// "Text" is what is show in bold
			String widgetText = "";
			// And the "Comment" is less visible, below the "Text"
			String widgetComment = "";

			// Construct period of counting...
			String widgetTime = "";
			if (data.dateUpdated == 0) {
				widgetTime = "==0 ???";
				Log.e(TAG, "data.dateUpdated==0");
			} else {
				widgetTime = formatWidgetTime(context, data.dateCleared, data.dateUpdated);
			}

			boolean isFound = false;

			if (data.numMentions > 0) {
				isFound = true;
				widgetText += (widgetText.length() > 0 ? "\n" : "")
						+ formatMessage(context,
								R.string.appwidget_new_mention_format,
								data.numMentions,
								R.string.appwidget_mention_singular,
								R.string.appwidget_mention_plural);
			}
			if (data.numMessages > 0) {
				isFound = true;
				widgetText += (widgetText.length() > 0 ? "\n" : "")
						+ formatMessage(context,
								R.string.appwidget_new_message_format,
								data.numMessages,
								R.string.appwidget_message_singular,
								R.string.appwidget_message_plural);
			}
			if (data.numTweets > 0) {
				isFound = true;
				widgetText += (widgetText.length() > 0 ? "\n" : "")
						+ formatMessage(context,
								R.string.appwidget_new_tweet_format,
								data.numTweets, R.string.appwidget_tweet_singular,
								R.string.appwidget_tweet_plural);
			}
			if (!isFound) {
				widgetComment = data.nothingPref;
			}

			Log.d(TAG, "updateAppWidget text=\"" + widgetText.replaceAll("\n", "; ") + "\"; comment=\""
					+ widgetComment + "\"");

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

			// When user clicks on widget, launch main AndTweet activity
			// Intent defineIntent = new Intent(android.content.Intent.ACTION_MAIN);
			Intent defineIntent = new Intent(context,
					com.xorcode.andtweet.TweetListActivity.class);

			PendingIntent pendingIntent = PendingIntent.getActivity(context,
					0 /* no requestCode */, defineIntent, 0 /* no flags */);
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
			Log.d(TAG, "inst=" + instanceId + "; updateAppWidget " + (Ok ? "succeded" : "failed") );
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
			
			
			/*
			if (DateUtils.isToday(endMillis)) {
	            flags |= DateUtils.FORMAT_SHOW_TIME;
	            if (DateFormat.is24HourFormat(context)) {
	                flags |= DateUtils.FORMAT_24HOUR;
	            }
			} else {
	            flags |= DateUtils.FORMAT_SHOW_DATE;
			}

			widgetTime = DateUtils.formatDateRange(context, startMillis, endMillis, flags);
			*/

			/*
			if (timeStart.yearDay < timeEnd.yearDay) {
				strStart = new RelativeTime(startMillis).getDifference(context, timeNow.toMillis(false));
				if (DateUtils.isToday(endMillis)) {
					// End - today
				} else {
					strEnd = new RelativeTime(endMillis).getDifference(context, timeNow.toMillis(false));
				}
			}
			
			widgetTime = android.text.format.DateUtils
					.formatDateTime(
							context,
							startMillis,
							android.text.format.DateUtils.FORMAT_SHOW_TIME
									| android.text.format.DateUtils.FORMAT_SHOW_DATE
									| android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY);
			  widgetTime = RelativeTime.getDifference(context,
			  startMillis);
	
	
			widgetTime = android.text.format.DateUtils.formatSameDayTime(
					startMillis, System.currentTimeMillis(),
					java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
					.toString();
	
			if (endMillis != startMillis) {
				String widgetTime2 = android.text.format.DateUtils
						.formatSameDayTime(endMillis,
								System.currentTimeMillis(),
								java.text.DateFormat.SHORT,
								java.text.DateFormat.SHORT).toString();
				if (widgetTime.compareTo(widgetTime2) != 0) {
					widgetTime += " - " + widgetTime2;
				}
			}
			 */
		}		
		
		return widgetTime;
	}

	private String formatMessage(Context context, int messageFormat,
			int numSomething, int singular, int plural) {
		String aMessage = "";
		java.text.MessageFormat form = new java.text.MessageFormat(context
				.getText(messageFormat).toString());
		Object[] formArgs = new Object[] { numSomething };
		double[] tweetLimits = { 1, 2 };
		String[] tweetPart = { context.getText(singular).toString(),
				context.getText(plural).toString() };
		java.text.ChoiceFormat tweetForm = new java.text.ChoiceFormat(
				tweetLimits, tweetPart);
		form.setFormatByArgumentIndex(0, tweetForm);
		aMessage = form.format(formArgs);
		return aMessage;
	}
}
