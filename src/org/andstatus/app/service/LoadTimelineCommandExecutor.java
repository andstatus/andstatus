/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.Uri;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public class LoadTimelineCommandExecutor implements OneCommandExecutor, OneCommandExecutorParent{
    private OneCommandExecutorParent parent;

    private CommandData commandData;
    private Context context;

    private boolean mNotificationsEnabled;
    private boolean mNotificationsVibrate;
    
    public LoadTimelineCommandExecutor(CommandData commandData) {
        this.commandData = commandData;
        context = MyContextHolder.get().context();

        mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
        mNotificationsVibrate = MyPreferences.getDefaultSharedPreferences().getBoolean("vibration", false);
    }

    /* (non-Javadoc)
     * @see org.andstatus.app.service.OneCommandExecutor#setParent(org.andstatus.app.service.OneCommandExecutorParent)
     */
    @Override
    public OneCommandExecutor setParent(OneCommandExecutorParent parent) {
        this.parent = parent;
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.andstatus.app.service.OneCommandExecutor#execute()
     */
    @Override
    public void execute() {
        MyLog.d(this, "Executing " + commandData);
        switch (commandData.getCommand()) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                loadTimeline(commandData);
                break;
            default:
                MyLog.e(this, "Unexpected command here " + commandData);
                break;
        }
    }
    
    private void logConnectionException(ConnectionException e, CommandData commandData, String detailedMessage) {
        if (e.isHardError()) {
            commandData.getResult().incrementParseExceptions();
        } else {
            commandData.getResult().incrementNumIoExceptions();
        }
        MyLog.e(this, detailedMessage + ": " + e.toString());
    }
    
    /**
     * Load one or all timeline(s) for one or all accounts
     * @return True if everything Succeeded
     */
    private void loadTimeline(CommandData commandData) {
        if (commandData.getAccount() == null) {
            for (MyAccount acc : MyContextHolder.get().persistentAccounts().collection()) {
                loadTimelineAccount(commandData, acc);
                if (isStopping()) {
                    setSoftErrorIfNotOk(commandData, false);
                    break;
                }
            }
        } else {
            loadTimelineAccount(commandData, commandData.getAccount());
        }
        if (!commandData.getResult().hasError() && commandData.getTimelineType() == TimelineTypeEnum.ALL && !isStopping()) {
            new DataPruner(context).prune();
        }
        if (!commandData.getResult().hasError()) {
            // Notify all timelines, 
            // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
            MyContextHolder.get().context().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
        }
    }

    /**
     * Load Timeline(s) for one MyAccount
     * @return True if everything Succeeded
     */
    private void loadTimelineAccount(CommandData commandData, MyAccount acc) {
        if (setErrorIfCredentialsNotVerified(commandData, acc)) {
            return;
        }
        boolean okAllTimelines = true;
        boolean ok = false;
        MessageCounters counters = new MessageCounters(acc, context, TimelineTypeEnum.ALL);
        String descr = "(starting)";

        long userId = commandData.itemId;
        if (userId == 0) {
            userId = acc.getUserId();
        }

        TimelineTypeEnum[] atl;
        if (commandData.getTimelineType() == TimelineTypeEnum.ALL) {
            atl = new TimelineTypeEnum[] {
                    TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                    TimelineTypeEnum.DIRECT,
                    TimelineTypeEnum.FOLLOWING_USER
            };
        } else {
            atl = new TimelineTypeEnum[] {
                    commandData.getTimelineType()
            };
        }

        int pass = 1;
        boolean okSomething = false;
        boolean notOkSomething = false;
        boolean[] oKs = new boolean[atl.length];
        try {
            for (int ind = 0; ind <= atl.length; ind++) {
                if (isStopping()) {
                    okAllTimelines = false;
                    break;
                }

                if (ind == atl.length) {
                    // This is some trick for the cases
                    // when we load more than one timeline at once
                    // and there was an error on some timeline only
                    if (pass > 1 || !okSomething || !notOkSomething) {
                        break;
                    }
                    pass++;
                    ind = 0; // Start from beginning
                    MyLog.d(this, "Second pass of loading timeline");
                }
                if (pass > 1) {
                    // Find next error index
                    for (int ind2 = ind; ind2 < atl.length; ind2++) {
                        if (!oKs[ind2]) {
                            ind = ind2;
                            break;
                        }
                    }
                    if (oKs[ind]) {
                        // No more errors on the second pass
                        break;
                    }
                }
                ok = true;
                TimelineTypeEnum timelineType = atl[ind];
                if (acc.getConnection().isApiSupported(timelineType.getConnectionApiRoutine())) {
                    MyLog.d(this, "Getting " + timelineType.save() + " for "
                            + acc.getAccountName());
                    TimelineDownloader fl = null;
                    descr = "loading " + timelineType.save();
                    counters.setTimelineType(timelineType);
                    fl = TimelineDownloader.newInstance(counters, userId);
                    fl.download();
                    counters.accumulate();
                } else {
                    MyLog.v(this, "Not supported " + timelineType.save() + " for "
                            + acc.getAccountName());
                }

                if (ok) {
                    okSomething = true;
                } else {
                    notOkSomething = true;
                }
                oKs[ind] = ok;
            }
        } catch (ConnectionException e) {
            logConnectionException(e, commandData, descr +" Exception");
            ok = false;
        } catch (SQLiteConstraintException e) {
            MyLog.e(this, descr + ", SQLite Exception", e);
            ok = false;
        }

        if (ok) {
            descr = "notifying";
            notifyOfUpdatedTimeline(counters.getMessagesAdded(), counters.getMentionsAdded(), counters.getDirectedAdded());
        }

        String message = "";
        if (oKs.length <= 1) {
            message += (ok ? "Succeeded" : "Failed");
            okAllTimelines = ok;
        } else {
            int nOks = 0;
            for (int ind = 0; ind < oKs.length; ind++) {
                if (oKs[ind]) {
                    nOks += 1;
                }
            }
            if (nOks > 0) {
                message += "Succeded " + nOks;
                if (nOks < oKs.length) {
                    message += " of " + oKs.length;
                    okAllTimelines = false;
                }
            } else {
                message += "Failed " + oKs.length;
                okAllTimelines = false;
            }
            message += " times";
        }
        setSoftErrorIfNotOk(commandData, okAllTimelines);
        
        message += " getting " + commandData.getTimelineType().save()
                + " for " + acc.getAccountName() + counters.toString();
        MyLog.d(this, message);
    }
    
    /**
     * TODO: Different notifications for different Accounts
     * @param msgAdded Number of "Tweets" added
     * @param mentionsAdded
     * @param directedAdded
     */
    private void notifyOfUpdatedTimeline(int msgAdded, int mentionsAdded, int directedAdded) {
        boolean notified = false;
        if (mentionsAdded > 0) {
            notifyOfNewTweets(mentionsAdded, CommandEnum.NOTIFY_MENTIONS);
            notified = true;
        }
        if (directedAdded > 0) {
            notifyOfNewTweets(directedAdded, CommandEnum.NOTIFY_DIRECT_MESSAGE);
            notified = true;
        }
        if (msgAdded > 0 || !notified) {
            notifyOfNewTweets(msgAdded, CommandEnum.NOTIFY_HOME_TIMELINE);
            notified = true;
        }
    }
    
    private void setSoftErrorIfNotOk(CommandData commandData, boolean ok) {
        if (!ok) {
            commandData.getResult().incrementNumIoExceptions();
        }
    }

    private boolean setErrorIfCredentialsNotVerified(CommandData commandData, MyAccount myAccount) {
        boolean errorOccured = false;
        if (myAccount == null || myAccount.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            errorOccured = true;
            commandData.getResult().incrementNumAuthExceptions();
        }
        return errorOccured;
    }
    
    
    /**
     * Notify the user of new tweets.
     * 
     * @param numHomeTimeline
     */
    private void notifyOfNewTweets(int numTweets, CommandEnum msgType) {
        MyLog.d(this, "notifyOfNewTweets n=" + numTweets + "; msgType=" + msgType);

        if (MyService.UPDATE_WIDGETS_ON_EVERY_UPDATE) {
            // Notify widgets even about the fact, that update occurred
            // even if there was nothing new
            updateWidgets(numTweets, msgType);
        }

        // If no notifications are enabled, return
        if (!mNotificationsEnabled || numTweets == 0) {
            return;
        }

        boolean notificationsMessages = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_messages", false);
        boolean notificationsReplies = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_mentions", false);
        boolean notificationsTimeline = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_timeline", false);
        String ringtone = MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_RINGTONE_PREFERENCE, null);

        // Make sure that notifications haven't been turned off for the
        // message type
        switch (msgType) {
            case NOTIFY_MENTIONS:
                if (!notificationsReplies) {
                    return;
                }
                break;
            case NOTIFY_DIRECT_MESSAGE:
                if (!notificationsMessages) {
                    return;
                }
                break;
            case NOTIFY_HOME_TIMELINE:
                if (!notificationsTimeline) {
                    return;
                }
                break;
            default:
                break;
        }

        // Set up the notification to display to the user
        Notification notification = new Notification(R.drawable.notification_icon,
                context.getText(R.string.notification_title), System.currentTimeMillis());

        notification.vibrate = null;
        if (mNotificationsVibrate) {
            notification.vibrate = new long[] {
                    200, 300, 200, 300
            };
        }

        notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
        notification.ledOffMS = 1000;
        notification.ledOnMS = 500;
        notification.ledARGB = Color.GREEN;

        if ("".equals(ringtone) || ringtone == null) {
            notification.sound = null;
        } else {
            Uri ringtoneUri = Uri.parse(ringtone);
            notification.sound = ringtoneUri;
        }

        // Set up the pending intent
        PendingIntent contentIntent;

        int messageTitle;
        Intent intent;
        String aMessage = "";

        // Prepare "intent" to launch timeline activities exactly like in
        // org.andstatus.app.TimelineActivity.onOptionsItemSelected
        switch (msgType) {
            case NOTIFY_MENTIONS:
                aMessage = I18n.formatQuantityMessage(context,
                        R.string.notification_new_mention_format, numTweets,
                        R.array.notification_mention_patterns,
                        R.array.notification_mention_formats);
                messageTitle = R.string.notification_title_mentions;
                intent = new Intent(context, TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.MENTIONS.save());
                contentIntent = PendingIntent.getActivity(context, numTweets,
                        intent, 0);
                break;

            case NOTIFY_DIRECT_MESSAGE:
                aMessage = I18n.formatQuantityMessage(context,
                        R.string.notification_new_message_format, numTweets,
                        R.array.notification_message_patterns,
                        R.array.notification_message_formats);
                messageTitle = R.string.notification_title_messages;
                intent = new Intent(context, TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.DIRECT.save());
                contentIntent = PendingIntent.getActivity(context, numTweets,
                        intent, 0);
                break;

            case NOTIFY_HOME_TIMELINE:
            default:
                aMessage = I18n
                        .formatQuantityMessage(context,
                                R.string.notification_new_tweet_format, numTweets,
                                R.array.notification_tweet_patterns,
                                R.array.notification_tweet_formats);
                messageTitle = R.string.notification_title;
                intent = new Intent(context, TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.HOME.save());
                contentIntent = PendingIntent.getActivity(context, numTweets,
                        intent, 0);
                break;
        }

        // Set up the scrolling message of the notification
        notification.tickerText = aMessage;

        // Set the latest event information and send the notification
        notification.setLatestEventInfo(context, context.getText(messageTitle), aMessage,
                contentIntent);
        NotificationManager nM = (NotificationManager) context.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.notify(msgType.ordinal(), notification);
    }

    /**
     * Send Update intent to AndStatus Widget(s), if there are some
     * installed... (e.g. on the Home screen...)
     * 
     * @see MyAppWidgetProvider
     */
    private void updateWidgets(int numTweets, CommandEnum msgType) {
        Intent intent = new Intent(MyService.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(IntentExtra.EXTRA_MSGTYPE.key, msgType.save());
        intent.putExtra(IntentExtra.EXTRA_NUMTWEETS.key, numTweets);
        context.sendBroadcast(intent);
    }

    @Override
    public boolean isStopping() {
        if (parent != null) {
            return parent.isStopping();
        } else {
            return false;
        }
    }
}
