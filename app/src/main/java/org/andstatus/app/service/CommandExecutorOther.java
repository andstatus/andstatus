/* 
 * Copyright (c) 2011-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils;

import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbRateLimitStatus;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

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
                followOrStopFollowingUser(execContext.getCommandData().getUserId(),
                        execContext.getCommandData().getCommand() == CommandEnum.FOLLOW_USER);
                break;
            case UPDATE_STATUS:
                updateStatus(execContext.getCommandData().itemId);
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
            case GET_USER:
                getUser(execContext.getCommandData().getUserId(), execContext.getCommandData().getUserName());
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
                (new AvatarDownloader(execContext.getCommandData().getUserId())).load(execContext.getCommandData());
                break;
            case NOTIFY_CLEAR:
                AppWidgets.clearAndUpdateWidgets(execContext.getMyContext());
                break;
            default:
                MyLog.e(this, "Unexpected command here " + execContext.getCommandData());
                break;
        }
    }

    private void getUser(long userId, String userName) {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
        String msgLog = "Get user oid=" + oid + ", userName='" + userName + "'";
        MbUser user = null;
        boolean errorLogged = false;
        if (MbUser.isOidReal(oid) || !TextUtils.isEmpty(userName)) {
            try {
                user = execContext.getMyAccount().getConnection().getUser(oid, userName);
                ok = !user.isEmpty();
            } catch (ConnectionException e) {
                errorLogged = true;
                logConnectionException(e, msgLog);
            }
        } else {
            MyLog.e(this, msgLog + "; userId not found: " + userId);
        }
        if (ok) {
            new DataInserter(execContext).insertOrUpdateUser(user);
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, (msgLog + (ok ? " succeeded" : " failed") ));
    }

    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(long msgId, boolean create) {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.MSG_OID, msgId, 0);
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
            if (message.favoritedByActor.toBoolean(!create) != create) {
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
                + (ok ? "succeeded" : "failed") + ", id=" + msgId);
    }

    /**
     * @param userId
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingUser(long userId, boolean follow) {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
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
            }
        }
        logOk(ok || !errorLogged);
        MyLog.d(this, (follow ? "Follow" : "Stop following") + " User "
                + (ok ? "succeeded" : "failed") + ", id=" + userId);
    }
    
    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyStatus(long msgId) {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.MSG_OID, msgId, 0);
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
                execContext.getContext().getContentResolver()
                        .delete(MatchedUri.getMsgUri(0, msgId), null, null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying status locally", e);
            }
        }
        MyLog.d(this, "Destroying status " + (ok ? "succeeded" : "failed") + ", id=" + msgId);
    }


    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyReblog(long msgId) {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.REBLOG_OID, msgId, execContext.getMyAccount().getUserId());
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
                values.put(MsgOfUserTable.REBLOGGED, 0);
                values.putNull(MsgOfUserTable.REBLOG_OID);
                Uri msgUri = MatchedUri.getMsgUri(execContext.getMyAccount().getUserId(), msgId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying reblog locally", e);
            }
        }
        MyLog.d(this, "Destroying reblog " + (ok ? "succeeded" : "failed") + ", id=" + msgId);
    }

    private void getStatus() {
        boolean ok = false;
        String oid = MyQuery.idToOid(OidEnum.MSG_OID, execContext.getCommandData().itemId, 0);
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
        MyLog.d(this, "getStatus " + (ok ? "succeeded" : "failed") + ", id=" + execContext.getCommandData().itemId);
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
    
    private void updateStatus(long msgId) {
        final String method = "updateStatus";
        boolean ok = false;
        MbMessage message = null;
        String status = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        long recipientUserId = MyQuery.msgIdToLongColumnValue(MsgTable.RECIPIENT_ID, msgId);
        DownloadData dd = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY);
        Uri mediaUri = dd.getUri().equals(Uri.EMPTY) ? Uri.EMPTY : FileProvider.downloadFilenameToUri(dd.getFile().getFilename());
        String msgLog = "text:'" + MyLog.trimmedString(status, 40) + "'"
                + (mediaUri.equals(Uri.EMPTY) ? "" : "; uri:'" + mediaUri + "'");
        try {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + ";" + msgLog);
            }
            DownloadStatus statusStored = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, msgId));
            if (!statusStored.mayBeSent()) {
                throw ConnectionException.hardConnectionException("Wrong message status: " + statusStored, null);
            }
            if (recipientUserId == 0) {
                long replyToMsgId = MyQuery.msgIdToLongColumnValue(
                        MsgTable.IN_REPLY_TO_MSG_ID, msgId);
                String replyToMsgOid = MyQuery.idToOid(OidEnum.MSG_OID, replyToMsgId, 0);
                message = execContext.getMyAccount().getConnection()
                        .updateStatus(status.trim(), replyToMsgOid, mediaUri);
            } else {
                String recipientOid = MyQuery.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                // Currently we don't use Screen Name, I guess id is enough.
                message = execContext.getMyAccount().getConnection()
                        .postDirectMessage(status.trim(), recipientOid, mediaUri);
            }
            ok = (!message.isEmpty());
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, method + "; " + msgLog);
        }
        if (ok) {
            // The message was sent successfully, so now update unsent message
            // New User's message should be put into the user's Home timeline.
            message.msgId = msgId;
            new DataInserter(
                    execContext.setTimelineType((recipientUserId == 0) ? TimelineType.HOME
                            : TimelineType.DIRECT)).insertOrUpdateMsg(message);
            execContext.getResult().setItemId(msgId);
        }
    }

    private void reblog(long rebloggedId) {
        String oid = MyQuery.idToOid(OidEnum.MSG_OID, rebloggedId, 0);
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
                    setTimelineType(TimelineType.HOME)).insertOrUpdateMsg(result);
        }
    }
    
    private void rateLimitStatus() {
        try {
            MbRateLimitStatus rateLimitStatus = execContext.getMyAccount().getConnection().rateLimitStatus();
            boolean ok = !rateLimitStatus.isEmpty();
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
