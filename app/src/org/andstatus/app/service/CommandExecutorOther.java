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

import android.content.ContentValues;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbRateLimitStatus;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

class CommandExecutorOther extends CommandExecutorStrategy{
    
    @Override
    public void execute() {
        switch (execContext.getCommandData().getCommand()) {
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
                createOrDestroyFavorite(execContext.getCommandData().itemId, 
                        execContext.getCommandData().getCommand() == CommandEnum.CREATE_FAVORITE);
                break;
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
                followOrStopFollowingUser(execContext.getCommandData().itemId, 
                        execContext.getCommandData().getCommand() == CommandEnum.FOLLOW_USER);
                break;
            case UPDATE_STATUS:
                String status = execContext.getCommandData().bundle.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key).trim();
                long replyToId = execContext.getCommandData().bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key);
                long recipientId = execContext.getCommandData().bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key);
                Uri mediaUri = UriUtils.fromString(execContext.getCommandData().bundle.getString(IntentExtra.EXTRA_MEDIA_URI.key));
                updateStatus(status, replyToId, recipientId, mediaUri);
                break;
            case DESTROY_STATUS:
                destroyStatus(execContext.getCommandData().itemId);
                break;
            case DESTROY_REBLOG:
                destroyReblog(execContext.getCommandData().itemId);
                break;
            case GET_STATUS:
                getStatus();
                break;
            case REBLOG:
                reblog(execContext.getCommandData().itemId);
                break;
            case RATE_LIMIT_STATUS:
                rateLimitStatus();
                break;
            case FETCH_ATTACHMENT:
                FileDownloader.newForDownloadRow(execContext.getCommandData().itemId).load(execContext.getCommandData());
                break;
            case FETCH_AVATAR:
                FileDownloader.newForUser(execContext.getCommandData().itemId).load(execContext.getCommandData());
                break;
            case NOTIFY_CLEAR:
                AppWidgets.clearAndUpdateWidgets(execContext.getMyContext());
                break;
            default:
                MyLog.e(this, "Unexpected command here " + execContext.getCommandData());
                break;
        }
    }

    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(long msgId, boolean create) {
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
        MbMessage message = null;
        boolean errorLogged = false;
        if (oid.length() > 0) {
            try {
                if (create) {
                    message = execContext.getMyAccount().getConnection().createFavorite(oid);
                } else {
                    message = execContext.getMyAccount().getConnection().destroyFavorite(oid);
                }
                ok = !message.isEmpty();
            } catch (ConnectionException e) {
                errorLogged = true;
                logConnectionException(e, (create ? "Create" : "Destroy") + " Favorite " + oid);
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
                new DataInserter(execContext).insertOrUpdateMsg(message);
            }
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, (create ? "Creating" : "Destroying") + " favorite "
                + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }

    /**
     * @param userId
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingUser(long userId, boolean follow) {
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        MbUser user = null;
        boolean errorLogged = false;
        if (oid.length() > 0) {
            try {
                user = execContext.getMyAccount().getConnection().followUser(oid, follow);
                ok = !user.isEmpty();
            } catch (ConnectionException e) {
                errorLogged = true;
                logConnectionException(e, follow ? "Follow" : "Stop following " + oid);
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
                new DataInserter(execContext).insertOrUpdateUser(user);
                execContext.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
            }
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, (follow ? "Follow" : "Stop following") + " User "
                + (ok ? "succeded" : "failed") + ", id=" + userId);
    }
    
    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyStatus(long msgId) {
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, msgId, 0);
        try {
            if (TextUtils.isEmpty(oid)) {
                ok = true;
                MyLog.e(this, "OID is empty for MsgId=" + msgId);
            } else {
                ok = execContext.getMyAccount().getConnection().destroyStatus(oid);
                logOk(ok);
            }
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
                ok = true;
            } else {
                logConnectionException(e, "Destroy Status " + oid);
            }
        }

        if (ok) {
            // And delete the status from the local storage
            try {
                // TODO: Maybe we should use Timeline Uri...
                execContext.getContext().getContentResolver()
                        .delete(MyProvider.MSG_CONTENT_URI, BaseColumns._ID + " = " + msgId, 
                                null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying status locally", e);
            }
        }
        MyLog.d(this, "Destroying status " + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }


    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyReblog(long msgId) {
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.REBLOG_OID, msgId, execContext.getMyAccount().getUserId());
        try {
            ok = execContext.getMyAccount().getConnection().destroyStatus(oid);
            logOk(ok);
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
                ok = true;
            } else {
                logConnectionException(e, "Destroy Reblog " + oid);
            }
        }

        if (ok) {
            // And delete the status from the local storage
            try {
                ContentValues values = new ContentValues();
                values.put(MyDatabase.MsgOfUser.REBLOGGED, 0);
                values.putNull(MyDatabase.MsgOfUser.REBLOG_OID);
                Uri msgUri = MyProvider.getTimelineMsgUri(execContext.getMyAccount().getUserId(), TimelineTypeEnum.HOME, false, msgId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying reblog locally", e);
            }
        }
        MyLog.d(this, "Destroying reblog " + (ok ? "succeded" : "failed") + ", id=" + msgId);
    }

    private void getStatus() {
        boolean ok = false;
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, execContext.getCommandData().itemId, 0);
        if (TextUtils.isEmpty(oid)) {
            execContext.getResult().incrementParseExceptions();
            MyLog.w(this, "getStatus failed, no OID for id=" + execContext.getCommandData().itemId);
            return;
        }
        try {
            MbMessage message = execContext.getMyAccount().getConnection().getMessage(oid);
            if (!message.isEmpty()) {
                ok = addMessageToLocalStorage(message);
            }
            logOk(ok);
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                execContext.getResult().incrementParseExceptions();
                // This means that there is no such "Status"
                // TODO: so we don't need to retry this command
            }
            logConnectionException(e, "getStatus " + oid);
        }
        MyLog.d(this, "getStatus " + (ok ? "succeded" : "failed") + ", id=" + execContext.getCommandData().itemId);
    }

    private boolean addMessageToLocalStorage(MbMessage message) {
        boolean ok = false;
        try {
            new DataInserter(execContext).insertOrUpdateMsg(message);
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
     * @param mediaUri 
     */
    private void updateStatus(String status, long replyToMsgId, long recipientUserId, Uri mediaUri) {
        final String method = "updateStatus";
        boolean ok = false;
        MbMessage message = null;
        try {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + ", text:'" + MyLog.trimmedString(status, 40) + "'");
            }
            if (recipientUserId == 0) {
                String replyToMsgOid = MyProvider.idToOid(OidEnum.MSG_OID, replyToMsgId, 0);
                message = execContext.getMyAccount().getConnection()
                        .updateStatus(status.trim(), replyToMsgOid, mediaUri);
            } else {
                String recipientOid = MyProvider.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                // Currently we don't use Screen Name, I guess id is enough.
                message = execContext.getMyAccount().getConnection()
                        .postDirectMessage(status.trim(), recipientOid, mediaUri);
            }
            ok = (!message.isEmpty());
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, method + ", text:'" + MyLog.trimmedString(status, 40) + "'");
        }
        if (ok) {
            // The message was sent successfully
            // New User's message should be put into the user's Home timeline.
            long msgId = new DataInserter(
                    execContext.setTimelineType((recipientUserId == 0) ? TimelineTypeEnum.HOME
                            : TimelineTypeEnum.DIRECT)).insertOrUpdateMsg(message);
            execContext.getResult().setItemId(msgId);
        }
    }

    private void reblog(long rebloggedId) {
        String oid = MyProvider.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
        boolean ok = false;
        MbMessage result = null;
        try {
            result = execContext.getMyAccount().getConnection()
                    .postReblog(oid);
            ok = !result.isEmpty();
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, "Reblog " + oid);
        }
        if (ok) {
            // The tweet was sent successfully
            // Reblog should be put into the user's Home timeline!
            new DataInserter(execContext.
                    setTimelineType(TimelineTypeEnum.HOME)).insertOrUpdateMsg(result);
        }
    }
    
    private void rateLimitStatus() {
        boolean ok = false;
        try {
            MbRateLimitStatus rateLimitStatus = execContext.getMyAccount().getConnection().rateLimitStatus();
            ok = !rateLimitStatus.isEmpty();
            if (ok) {
                execContext.getResult().setRemainingHits(rateLimitStatus.remaining); 
                execContext.getResult().setHourlyLimit(rateLimitStatus.limit);
             }
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, "rateLimitStatus");
        }
    }
}
