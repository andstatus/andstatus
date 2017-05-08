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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbRateLimitStatus;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.List;

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
            case GET_CONVERSATION:
                getConversation(execContext.getCommandData().itemId);
                break;
            case GET_STATUS:
                getStatus(execContext.getCommandData().itemId);
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
            case CLEAR_NOTIFICATIONS:
                AppWidgets.clearAndUpdateWidgets(execContext.getMyContext());
                break;
            default:
                MyLog.e(this, "Unexpected command here " + execContext.getCommandData());
                break;
        }
    }

    private void getConversation(long msgId) {
        final String method = "getConversation";
        String conversationOid = MyQuery.msgIdToConversationOid(msgId);
        if (TextUtils.isEmpty(conversationOid)) {
            logExecutionError(true, method + " empty conversationId " + MyQuery.msgInfoForLog(msgId));
        } else {
            try {
                List<MbTimelineItem> messages = execContext.getMyAccount().getConnection().getConversation(conversationOid);
                if (!messages.isEmpty()) {
                    DataInserter di = new DataInserter(execContext);
                    for (MbTimelineItem item : messages) {
                        switch (item.getType()) {
                            case MESSAGE:
                                di.insertOrUpdateMsg(item.mbMessage);
                                break;
                            default:
                                break;
                        }
                    }
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    execContext.getResult().incrementParseExceptions();
                }
                logConnectionException(e, method + " " + MyQuery.msgInfoForLog(msgId));
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void getUser(long userId, String userName) {
        final String method = "getUser";
        String oid = getUserOid(method, userId, false);
        String msgLog = method + "; userName='" + userName + "'";
        MbUser user = null;
        if (MbUser.isOidReal(oid) || !TextUtils.isEmpty(userName)) {
            try {
                user = execContext.getMyAccount().getConnection().getUser(oid, userName);
                logIfUserIsEmpty(msgLog, userId, user);
            } catch (ConnectionException e) {
                logConnectionException(e, msgLog + userInfoLogged(userId));
            }
        } else {
            msgLog += ", invalid user IDs";
            logExecutionError(true, msgLog + userInfoLogged(userId));
        }
        if (noErrors()) {
            new DataInserter(execContext).insertOrUpdateUser(user);
        }
        MyLog.d(this, (msgLog + (noErrors() ? " succeeded" : " failed") ));
    }

    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(long msgId, boolean create) {
        final String method = (create ? "create" : "destroy") + "Favorite";
        String oid = getMsgOid(method, msgId, true);
        MbMessage message = null;
        if (noErrors()) {
            try {
                if (create) {
                    message = execContext.getMyAccount().getConnection().createFavorite(oid);
                } else {
                    message = execContext.getMyAccount().getConnection().destroyFavorite(oid);
                }
                logIfEmptyMessage(method, msgId, message);
            } catch (ConnectionException e) {
                logConnectionException(e, method + "; " + MyQuery.msgInfoForLog(msgId));
            }
        }
        if (noErrors()) {
            if (message.getFavoritedByMe().toBoolean(!create) != create) {
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
                    message.setFavoritedByMe(TriState.fromBoolean(create));

                    MyLog.d(this, method + "; Favorited flag didn't change yet.");
                    // Let's try to assume that everything was OK
                } else {
                    // yvolk: 2011-09-27 Sometimes this
                    // twitter.com 'async' process doesn't work
                    // so let's try another time...
                    // This is safe, because "delete favorite"
                    // works even for the "Unfavorited" tweet :-)
                    logExecutionError(false, method + "; Favorited flag didn't change yet. " + MyQuery.msgInfoForLog(msgId));
                }
            }

            if (noErrors()) {
                // Please note that the Favorited message may be NOT in the User's Home timeline!
                new DataInserter(execContext).insertOrUpdateMsg(message);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    @NonNull
    private String getMsgOid(String method, long msgId, boolean required) {
        String oid = MyQuery.idToOid(OidEnum.MSG_OID, msgId, 0);
        if (required && TextUtils.isEmpty(oid)) {
            logExecutionError(true, method + "; no Message ID in the Social Network " + MyQuery.msgInfoForLog(msgId));
        }
        return oid;
    }

    /**
     * @param userId
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingUser(long userId, boolean follow) {
        final String method = (follow ? "follow" : "stopFollowing") + "User";
        String oid = getUserOid(method, userId, true);
        MbUser user = null;
        if (noErrors()) {
            try {
                user = execContext.getMyAccount().getConnection().followUser(oid, follow);
                logIfUserIsEmpty(method, userId, user);
            } catch (ConnectionException e) {
                logConnectionException(e, method + userInfoLogged(userId));
            }
        }
        if (noErrors()) {
            if (user.followedByActor != TriState.UNKNOWN &&  user.followedByActor.toBoolean(follow) != follow) {
                if (follow) {
                    // Act just like for creating favorite...
                    user.followedByActor = TriState.fromBoolean(follow);

                    MyLog.d(this, "Follow a User. 'following' flag didn't change yet.");
                    // Let's try to assume that everything was OK:
                } else {
                    logExecutionError(false, "'following' flag didn't change yet, " + method + userInfoLogged(userId));
                }
            }
            if (noErrors()) {
                new DataInserter(execContext).insertOrUpdateUser(user);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfUserIsEmpty(String method, long userId, MbUser user) {
        if (user == null || user.isEmpty()) {
            logExecutionError(false, "Received User is empty, " + method + userInfoLogged(userId));
        }
    }

    @NonNull
    private String getUserOid(String method, long userId, boolean required) {
        String oid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
        if (required && TextUtils.isEmpty(oid)) {
            logExecutionError(true, method + "; no User ID in the Social Network " + userInfoLogged(userId));
        }
        return oid;
    }

    private String userInfoLogged(long userId) {
        String oid = MyQuery.idToOid(OidEnum.USER_OID, userId, 0);
        return " userId=" + userId + ", oid" + (TextUtils.isEmpty(oid) ? " is empty" : "'" + oid + "'" +
                ", webFingerId:'" + MyQuery.userIdToWebfingerId(userId) + "'");
    }

    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyStatus(long msgId) {
        final String method = "destroyStatus";
        boolean ok = false;
        String oid = getMsgOid(method, msgId, false);
        try {
            if (msgId == 0 || TextUtils.isEmpty(oid)) {
                ok = true;
                MyLog.i(this, method + "; OID is empty for MsgId=" + msgId);
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
                logConnectionException(e, method + "; " + oid);
            }
        }
        if (ok && msgId != 0) {
            try {
                execContext.getContext().getContentResolver()
                        .delete(MatchedUri.getMsgUri(0, msgId), null, null);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying message locally", e);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyReblog(long msgId) {
        final String method = "destroyReblog";
        String oid = MyQuery.idToOid(OidEnum.REBLOG_OID, msgId, execContext.getMyAccount().getUserId());
        try {
            if (!execContext.getMyAccount().getConnection().destroyReblog(oid)) {
                logExecutionError(false, "Connection returned 'false' " + method + MyQuery.msgInfoForLog(msgId));
            }
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
            } else {
                logConnectionException(e, method + "; " + oid + MyQuery.msgInfoForLog(msgId));
            }
        }
        if (noErrors()) {
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
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void getStatus(long msgId) {
        final String method = "getStatus";
        String oid = getMsgOid(method, msgId, true);
        if (noErrors()) {
            try {
                MbMessage message = execContext.getMyAccount().getConnection().getMessage(oid);
                if (message.isEmpty()) {
                    logExecutionError(false, "Received Message is empty, " + MyQuery.msgInfoForLog(msgId));
                } else {
                    try {
                        new DataInserter(execContext).insertOrUpdateMsg(message);
                    } catch (Exception e) {
                        logExecutionError(false, "Error while saving to the local cache," + MyQuery.msgInfoForLog(msgId) +
                        ", " + e.getMessage());
                    }
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    execContext.getResult().incrementParseExceptions();
                    // This means that there is no such "Status"
                    // TODO: so we don't need to retry this command
                }
                logConnectionException(e, method + MyQuery.msgInfoForLog(msgId));
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void updateStatus(long msgId) {
        final String method = "updateStatus";
        MbMessage message = null;
        String status = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        String oid = getMsgOid(method, msgId, false);
        long recipientUserId = MyQuery.msgIdToLongColumnValue(MsgTable.RECIPIENT_ID, msgId);
        Uri mediaUri = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY).
                mediaUriToBePosted();
        String msgLog = "text:'" + MyLog.trimmedString(status, 40) + "'"
                + (mediaUri.equals(Uri.EMPTY) ? "" : "; mediaUri:'" + mediaUri + "'");
        try {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + ";" + msgLog);
            }
            DownloadStatus statusStored = DownloadStatus.load(
                    MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, msgId));
            if (!statusStored.mayBeSent()) {
                throw ConnectionException.hardConnectionException(
                        "Wrong message status: " + statusStored, null);
            }
            if (recipientUserId == 0) {
                long replyToMsgId = MyQuery.msgIdToLongColumnValue(
                        MsgTable.IN_REPLY_TO_MSG_ID, msgId);
                String replyToMsgOid = getMsgOid(method, replyToMsgId, false);
                message = execContext.getMyAccount().getConnection()
                        .updateStatus(status.trim(), oid, replyToMsgOid, mediaUri);
            } else {
                String recipientOid = MyQuery.idToOid(OidEnum.USER_OID, recipientUserId, 0);
                // Currently we don't use Screen Name, I guess id is enough.
                message = execContext.getMyAccount().getConnection()
                        .postDirectMessage(status.trim(), oid, recipientOid, mediaUri);
            }
            logIfEmptyMessage(method, msgId, message);
        } catch (ConnectionException e) {
            logConnectionException(e, method + "; " + msgLog);
        }
        if (noErrors()) {
            // The message was sent successfully, so now update unsent message
            // New User's message should be put into the user's Home timeline.
            message.msgId = msgId;
            new DataInserter(execContext).insertOrUpdateMsg(message);
            execContext.getResult().setItemId(msgId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfEmptyMessage(String method, long msgId, MbMessage message) {
        if (message == null || message.isEmpty()) {
            logExecutionError(false, method + "; Received Message is empty, " + MyQuery.msgInfoForLog(msgId));
        }
    }

    private void reblog(long rebloggedId) {
        final String method = "Reblog";
        String oid = getMsgOid(method, rebloggedId, true);
        MbMessage message = null;
        if (noErrors()) {
            try {
                message = execContext.getMyAccount().getConnection().postReblog(oid);
                logIfEmptyMessage(method, rebloggedId, message);
            } catch (ConnectionException e) {
                logConnectionException(e, "Reblog " + oid);
            }
        }
        if (noErrors()) {
            // The tweet was sent successfully
            // Reblog should be put into the user's Home timeline!
            new DataInserter(execContext).insertOrUpdateMsg(message);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
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
