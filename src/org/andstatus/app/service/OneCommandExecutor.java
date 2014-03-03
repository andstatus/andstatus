/* 
 * Copyright (c) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.R.array;
import org.andstatus.app.R.drawable;
import org.andstatus.app.R.string;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarLoader;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbRateLimitStatus;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

public class OneCommandExecutor {
    OneCommandExecutorParent parent;
    CommandData commandData;
    Context context;

    private boolean mNotificationsEnabled;
    private boolean mNotificationsVibrate;
    
    public OneCommandExecutor(OneCommandExecutorParent parent, CommandData commandData) {
        this.parent = parent;
        this.commandData = commandData;
        context = MyContextHolder.get().context();

        mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
        mNotificationsVibrate = MyPreferences.getDefaultSharedPreferences().getBoolean("vibration", false);
    }

    public void execute() {
        MyLog.d(this, "Executing " + commandData);
        switch (commandData.command) {
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
            case SEARCH_MESSAGE:
                loadTimeline(commandData);
                break;
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
                createOrDestroyFavorite(commandData,
                        commandData.itemId, 
                        commandData.command == CommandEnum.CREATE_FAVORITE);
                break;
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
                followOrStopFollowingUser(commandData,
                        commandData.itemId, 
                        commandData.command == CommandEnum.FOLLOW_USER);
                break;
            case UPDATE_STATUS:
                String status = commandData.bundle.getString(IntentExtra.EXTRA_STATUS.key).trim();
                long replyToId = commandData.bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key);
                long recipientId = commandData.bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key);
                updateStatus(commandData, status, replyToId, recipientId);
                break;
            case DESTROY_STATUS:
                destroyStatus(commandData, commandData.itemId);
                break;
            case DESTROY_REBLOG:
                destroyReblog(commandData, commandData.itemId);
                break;
            case GET_STATUS:
                getStatus(commandData);
                break;
            case REBLOG:
                reblog(commandData, commandData.itemId);
                break;
            case RATE_LIMIT_STATUS:
                rateLimitStatus(commandData);
                break;
            case FETCH_AVATAR:
                new AvatarLoader(commandData.itemId).load(commandData);
                break;
            default:
                MyLog.e(this, "Unexpected command here " + commandData);
                break;
        }
    }
    
    
    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(CommandData commandData, long msgId, boolean create) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        MyAccount ma = commandData.getAccount();
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
        MbMessage message = null;
        if (oid.length() > 0) {
            try {
                if (create) {
                    message = ma.getConnection().createFavorite(oid);
                } else {
                    message = ma.getConnection().destroyFavorite(oid);
                }
                ok = !message.isEmpty();
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, (create ? "create" : "destroy") + "Favorite Connection Exception");
            }
        } else {
            MyLog.e(this,
                    (create ? "create" : "destroy") + "Favorite; msgId not found: " + msgId);
        }
        if (ok) {
            if (SharedPreferencesUtil.isTrue(message.favoritedByActor) != create) {
                /**
                 * yvolk: 2011-09-27 Twitter docs state that
                 * this may happen due to asynchronous nature of
                 * the process, see
                 * https://dev.twitter.com/docs/
                 * api/1/post/favorites/create/%3Aid
                 */
                if (create) {
                    // For the case we created favorite, let's
                    // change
                    // the flag manually.
                    message.favoritedByActor = TriState.fromBoolean(create);

                    MyLog.d(this,
                            (create ? "create" : "destroy")
                                    + ". Favorited flag didn't change yet.");

                    // Let's try to assume that everything was
                    // Ok:
                    ok = true;
                } else {
                    // yvolk: 2011-09-27 Sometimes this
                    // twitter.com 'async' process doesn't work
                    // so let's try another time...
                    // This is safe, because "delete favorite"
                    // works even for the "Unfavorited" tweet :-)
                    ok = false;

                    MyLog.e(this,
                            (create ? "create" : "destroy")
                                    + ". Favorited flag didn't change yet.");
                }
            }

            if (ok) {
                // Please note that the Favorited message may be NOT in the User's Home timeline!
                new DataInserter(ma,
                        context,
                        TimelineTypeEnum.ALL).insertOrUpdateMsg(message);
            }
        }
        setSoftErrorIfNotOk(commandData, ok);
        MyLog.d(this, (create ? "Creating" : "Destroying") + " favorite "
                + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }

    /**
     * @param userId
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingUser(CommandData commandData, long userId, boolean follow) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        MyAccount ma = commandData.getAccount();
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        MbUser user = null;
        if (oid.length() > 0) {
            try {
                user = ma.getConnection().followUser(oid, follow);
                ok = !user.isEmpty();
            } catch (ConnectionException e) {
                logConnectionException(e, commandData, (follow ? "Follow" : "Stop following") + " Exception");
            }
        } else {
            MyLog.e(this,
                    (follow ? "Follow" : "Stop following") + " User; userId not found: " + userId);
        }
        if (ok) {
            if (user.followedByActor != TriState.UNKNOWN &&  user.followedByActor.toBoolean(follow) != follow) {
                if (follow) {
                    // Act just like for creating favorite...
                    user.followedByActor = TriState.fromBoolean(follow);

                    MyLog.d(this,
                            (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");

                    // Let's try to assume that everything was
                    // Ok:
                    ok = true;
                } else {
                    ok = false;

                    MyLog.e(this,
                            (follow ? "Follow" : "Stop following") + " User. 'following' flag didn't change yet.");
                }
            }
            if (ok) {
                new DataInserter(ma,
                        context,
                        TimelineTypeEnum.HOME).insertOrUpdateUser(user);
                context.getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
            }
        }
        setSoftErrorIfNotOk(commandData, ok);
        MyLog.d(this, (follow ? "Follow" : "Stop following") + " User "
                + (ok ? "succeded" : "failed") + ", id=" + userId);
    }
    
    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyStatus(CommandData commandData, long msgId) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        MyAccount ma = commandData.getAccount();
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
        try {
            ok = ma.getConnection().destroyStatus(oid);
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
                ok = true;
            } else {
                logConnectionException(e, commandData, "destroyStatus Exception");
            }
        }

        if (ok) {
            // And delete the status from the local storage
            try {
                // TODO: Maybe we should use Timeline Uri...
                context.getContentResolver()
                        .delete(MyProvider.MSG_CONTENT_URI, BaseColumns._ID + " = " + msgId, 
                                null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying status locally", e);
            }
        }
        setSoftErrorIfNotOk(commandData, ok);
        MyLog.d(this, "Destroying status " + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }


    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyReblog(CommandData commandData, long msgId) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        MyAccount ma = commandData.getAccount();
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.REBLOG_OID, msgId, ma.getUserId());
        try {
            ok = ma.getConnection().destroyStatus(oid);
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
                ok = true;
            } else {
                logConnectionException(e, commandData, "destroyReblog Exception");
            }
        }

        if (ok) {
            // And delete the status from the local storage
            try {
                ContentValues values = new ContentValues();
                values.put(MyDatabase.MsgOfUser.REBLOGGED, 0);
                values.putNull(MyDatabase.MsgOfUser.REBLOG_OID);
                Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.HOME, false, msgId);
                context.getContentResolver().update(msgUri, values, null, null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying reblog locally", e);
            }
        }
        setSoftErrorIfNotOk(commandData, ok);
        MyLog.d(this, "Destroying reblog " + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }

    private void getStatus(CommandData commandData) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, commandData.itemId, 0);
        try {
            MbMessage message = commandData.getAccount().getConnection().getMessage(oid);
            if (!message.isEmpty()) {
                ok = addMessageToLocalStorage(commandData, message);
            }
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                commandData.commandResult.numParseExceptions++;
                // This means that there is no such "Status"
                // TODO: so we don't need to retry this command
            }
            logConnectionException(e, commandData, "getStatus Exception");
        }
        setSoftErrorIfNotOk(commandData, ok);
        MyLog.d(this, "getStatus " + (ok ? "succeded" : "failed") + ", id=" + commandData.itemId);
    }

    private boolean addMessageToLocalStorage(CommandData commandData, MbMessage message) {
        boolean ok = false;
        try {
            new DataInserter(commandData.getAccount(),
                    context,
                    TimelineTypeEnum.ALL).insertOrUpdateMsg(message);
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, "Error inserting status", e);
        }
        return ok;
    }
    
    /**
     * @param status
     * @param replyToMsgId - Message Id
     * @param recipientUserId !=0 for Direct messages - User Id
     */
    private void updateStatus(CommandData commandData, String status, long replyToMsgId, long recipientUserId) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        MyAccount ma = commandData.getAccount();
        boolean ok = false;
        MbMessage message = null;
        try {
            if (recipientUserId == 0) {
                String replyToMsgOid = MyProvider.idToOid(OidEnum.MSG_OID, replyToMsgId, 0);
                message = ma.getConnection()
                        .updateStatus(status.trim(), replyToMsgOid);
            } else {
                String recipientOid = MyProvider.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                // Currently we don't use Screen Name, I guess id is enough.
                message = ma.getConnection()
                        .postDirectMessage(status.trim(), recipientOid);
            }
            ok = (!message.isEmpty());
        } catch (ConnectionException e) {
            logConnectionException(e, commandData, "updateStatus Exception");
        }
        if (ok) {
            // The message was sent successfully
            // New User's message should be put into the user's Home timeline.
            new DataInserter(ma, 
                    context,
                    (recipientUserId == 0) ? TimelineTypeEnum.HOME : TimelineTypeEnum.DIRECT)
            .insertOrUpdateMsg(message);
        }
        setSoftErrorIfNotOk(commandData, ok);
    }
    
    private void reblog(CommandData commandData, long rebloggedId) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
        boolean ok = false;
        MbMessage result = null;
        try {
            result = commandData.getAccount().getConnection()
                    .postReblog(oid);
            ok = !result.isEmpty();
        } catch (ConnectionException e) {
            logConnectionException(e, commandData, "Reblog Exception");
        }
        if (ok) {
            // The tweet was sent successfully
            // Reblog should be put into the user's Home timeline!
            new DataInserter(commandData.getAccount(), 
                    context,
                    TimelineTypeEnum.HOME).insertOrUpdateMsg(result);
        }
        setSoftErrorIfNotOk(commandData, ok);
    }
    
    private void logConnectionException(ConnectionException e, CommandData commandData, String detailedMessage) {
        if (e.isHardError()) {
            commandData.commandResult.numParseExceptions += 1;
        } else {
            commandData.commandResult.numIoExceptions += 1;
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
                if (parent.isStopping()) {
                    setSoftErrorIfNotOk(commandData, false);
                    break;
                }
            }
        } else {
            loadTimelineAccount(commandData, commandData.getAccount());
        }
        if (!commandData.commandResult.hasError() && commandData.timelineType == TimelineTypeEnum.ALL && !parent.isStopping()) {
            new DataPruner(context).prune();
        }
        if (!commandData.commandResult.hasError()) {
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
        if (commandData.timelineType == TimelineTypeEnum.ALL) {
            atl = new TimelineTypeEnum[] {
                    TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                    TimelineTypeEnum.DIRECT,
                    TimelineTypeEnum.FOLLOWING_USER
            };
        } else {
            atl = new TimelineTypeEnum[] {
                    commandData.timelineType
            };
        }

        int pass = 1;
        boolean okSomething = false;
        boolean notOkSomething = false;
        boolean oKs[] = new boolean[atl.length];
        try {
            for (int ind = 0; ind <= atl.length; ind++) {
                if (parent.isStopping()) {
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
                    counters.timelineType = timelineType;
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
            notifyOfUpdatedTimeline(counters.msgAdded, counters.mentionsAdded, counters.directedAdded);
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
        
        message += " getting " + commandData.timelineType.save()
                + " for " + acc.getAccountName() + counters.accumulatedToString();
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

    private void rateLimitStatus(CommandData commandData) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        boolean ok = false;
        try {
            Connection conn = commandData.getAccount().getConnection();
            MbRateLimitStatus rateLimitStatus = conn.rateLimitStatus();
            ok = !rateLimitStatus.isEmpty();
            if (ok) {
                commandData.commandResult.remainingHits = rateLimitStatus.remaining; 
                commandData.commandResult.hourlyLimit = rateLimitStatus.limit;
             }
        } catch (ConnectionException e) {
            logConnectionException(e, commandData, "rateLimitStatus Exception");
        }
        setSoftErrorIfNotOk(commandData, ok);
    }
    
    private void setSoftErrorIfNotOk(CommandData commandData, boolean ok) {
        if (!ok) {
            commandData.commandResult.numIoExceptions++;
        }
    }

    private boolean setErrorIfCredentialsNotVerified(CommandData commandData, MyAccount myAccount) {
        boolean errorOccured = false;
        if (myAccount == null || myAccount.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
            errorOccured = true;
            commandData.commandResult.numAuthExceptions++;
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
}
