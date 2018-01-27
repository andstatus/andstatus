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

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.RateLimitStatus;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.support.java.util.function.Supplier;
import org.andstatus.app.support.java.util.function.SupplierWithException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.List;

import static org.andstatus.app.data.MyProvider.deleteMessage;

class CommandExecutorOther extends CommandExecutorStrategy{

    public static final int USERS_LIMIT = 400;

    @Override
    public void execute() {
        switch (execContext.getCommandData().getCommand()) {
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
                createOrDestroyFavorite(execContext.getCommandData().itemId, 
                        execContext.getCommandData().getCommand() == CommandEnum.CREATE_FAVORITE);
                break;
            case FOLLOW_ACTOR:
            case STOP_FOLLOWING_ACTOR:
                followOrStopFollowingUser(execContext.getCommandData().getUserId(),
                        execContext.getCommandData().getCommand() == CommandEnum.FOLLOW_ACTOR);
                break;
            case UPDATE_NOTE:
                updateStatus(execContext.getCommandData().itemId);
                break;
            case DELETE_NOTE:
                destroyStatus(execContext.getCommandData().itemId);
                break;
            case DELETE_REBLOG:
                destroyReblog(execContext.getCommandData().itemId);
                break;
            case GET_CONVERSATION:
                getConversation(execContext.getCommandData().itemId);
                break;
            case GET_NOTE:
                getStatus(execContext.getCommandData().itemId);
                break;
            case GET_ACTOR:
                getActor(execContext.getCommandData().getUserId(), execContext.getCommandData().getActorName());
                break;
            case SEARCH_ACTORS:
                searchUsers(execContext.getCommandData().getActorName());
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
                execContext.getMyContext().clearNotification(execContext.getCommandData().getTimeline());
                break;
            default:
                MyLog.e(this, "Unexpected command here " + execContext.getCommandData());
                break;
        }
    }

    private void searchUsers(String searchQuery) {
        final String method = "searchUsers";
        String msgLog = method + "; query='" + searchQuery + "'";
        List<Actor> users = null;
        if (StringUtils.nonEmpty(searchQuery)) {
            try {
                users = execContext.getMyAccount().getConnection().searchActors(USERS_LIMIT, searchQuery);
                for (Actor user : users) {
                    new DataUpdater(execContext).onActivity(user.update(execContext.getMyAccount().getActor()));
                }
            } catch (ConnectionException e) {
                logConnectionException(e, msgLog);
            }
        } else {
            msgLog += ", empty query";
            logExecutionError(true, msgLog);
        }
        MyLog.d(this, (msgLog + (noErrors() ? " succeeded" : " failed") ));
    }

    private void getConversation(long msgId) {
        final String method = "getConversation";
        String conversationOid = MyQuery.msgIdToConversationOid(msgId);
        if (TextUtils.isEmpty(conversationOid)) {
            logExecutionError(true, method + " empty conversationId " + MyQuery.msgInfoForLog(msgId));
        } else {
            onActivities(method, () -> execContext.getMyAccount().getConnection().getConversation(conversationOid),
                    () -> MyQuery.msgInfoForLog(msgId));
        }
    }

    private void onActivities(String method, SupplierWithException<List<AActivity>, ConnectionException> supplier,
                              Supplier<String> contextInfoSupplier) {
        try {
            List<AActivity> activities = supplier.get();
            DataUpdater.onActivities(execContext, activities);
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                execContext.getResult().incrementParseExceptions();
            }
            logConnectionException(e, method + "; " + contextInfoSupplier.get());
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void getActor(long actorId, String userName) {
        final String method = "getUser";
        String oid = getActorOid(method, actorId, false);
        String msgLog = method + "; userName='" + userName + "'";
        Actor actor = null;
        if (UriUtils.isRealOid(oid) || !TextUtils.isEmpty(userName)) {
            try {
                actor = execContext.getMyAccount().getConnection().getActor(oid, userName);
                logIfUserIsEmpty(msgLog, actorId, actor);
            } catch (ConnectionException e) {
                logConnectionException(e, msgLog + userInfoLogged(actorId));
            }
        } else {
            msgLog += ", invalid user IDs";
            logExecutionError(true, msgLog + userInfoLogged(actorId));
        }
        if (noErrors() && actor != null) {
            new DataUpdater(execContext).onActivity(actor.update(execContext.getMyAccount().getActor()));
        }
        MyLog.d(this, (msgLog + (noErrors() ? " succeeded" : " failed") ));
    }

    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(long msgId, boolean create) {
        final String method = (create ? "create" : "destroy") + "Favorite";
        String oid = getMsgOid(method, msgId, true);
        AActivity activity = null;
        if (noErrors()) {
            try {
                if (create) {
                    activity = execContext.getMyAccount().getConnection().createFavorite(oid);
                } else {
                    activity = execContext.getMyAccount().getConnection().destroyFavorite(oid);
                }
                logIfEmptyMessage(method, msgId, activity.getMessage());
            } catch (ConnectionException e) {
                logConnectionException(e, method + "; " + MyQuery.msgInfoForLog(msgId));
            }
        }
        if (noErrors()) {
            if (!activity.type.equals(create ? ActivityType.LIKE : ActivityType.UNDO_LIKE)) {
                /*
                 * yvolk: 2011-09-27 Twitter docs state that
                 * this may happen due to asynchronous nature of
                 * the process, see
                 * https://dev.twitter.com/docs/
                 * api/1/post/favorites/create/%3Aid
                 */
                if (create) {
                    // For the case we created favorite, let's
                    // change the flag manually.
                    activity = activity.getMessage().act(activity.accountActor, activity.getActor(), ActivityType.LIKE);

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
                new DataUpdater(execContext).onActivity(activity);
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
     * @param actorId
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingUser(long actorId, boolean follow) {
        final String method = (follow ? "follow" : "stopFollowing") + "User";
        String oid = getActorOid(method, actorId, true);
        AActivity activity = null;
        if (noErrors()) {
            try {
                activity = execContext.getMyAccount().getConnection().followActor(oid, follow);
                logIfUserIsEmpty(method, actorId, activity.getObjActor());
            } catch (ConnectionException e) {
                logConnectionException(e, method + userInfoLogged(actorId));
            }
        }
        if (activity != null && noErrors()) {
            if (!activity.type.equals(follow ? ActivityType.FOLLOW : ActivityType.UNDO_FOLLOW)) {
                if (follow) {
                    // Act just like for creating favorite...
                    activity = activity.getObjActor().act(Actor.EMPTY, activity.getActor(), ActivityType.FOLLOW);
                    MyLog.d(this, "Follow a User. 'following' flag didn't change yet.");
                    // Let's try to assume that everything was OK:
                } else {
                    logExecutionError(false, "'following' flag didn't change yet, " + method + userInfoLogged(actorId));
                }
            }
            if (noErrors()) {
                new DataUpdater(execContext).onActivity(activity);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfUserIsEmpty(String method, long actorId, Actor user) {
        if (user == null || user.isEmpty()) {
            logExecutionError(false, "Received User is empty, " + method + userInfoLogged(actorId));
        }
    }

    @NonNull
    private String getActorOid(String method, long actorId, boolean required) {
        String oid = MyQuery.idToOid(OidEnum.ACTOR_OID, actorId, 0);
        if (required && TextUtils.isEmpty(oid)) {
            logExecutionError(true, method + "; no User ID in the Social Network " + userInfoLogged(actorId));
        }
        return oid;
    }

    private String userInfoLogged(long actorId) {
        String oid = getActorOid("userInfoLogged", actorId, false);
        return " actorId=" + actorId + ", oid" + (TextUtils.isEmpty(oid) ? " is empty" : "'" + oid + "'" +
                ", webFingerId:'" + MyQuery.actorIdToWebfingerId(actorId) + "'");
    }

    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyStatus(long msgId) {
        final String method = "destroyStatus";
        boolean ok = false;
        String oid = getMsgOid(method, msgId, false);
        DownloadStatus statusStored = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, msgId));
        try {
            if (msgId == 0 || TextUtils.isEmpty(oid) || statusStored != DownloadStatus.LOADED) {
                ok = true;
                MyLog.i(this, method + "; OID='" + oid + "', status='" + statusStored + "' for msgId=" + msgId);
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
            deleteMessage(execContext.getContext(), msgId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    /**
     * @param msgId ID of the message to destroy
     */
    private void destroyReblog(long msgId) {
        final String method = "destroyReblog";
        final long actorId = execContext.getMyAccount().getActorId();
        final Pair<Long, ActivityType> reblogAndType = MyQuery.msgIdToLastReblogging(
                execContext.getMyContext().getDatabase(), msgId, actorId);
        if (reblogAndType.second != ActivityType.ANNOUNCE) {
            logExecutionError(true, "No local Reblog of "
                    + MyQuery.msgInfoForLog(msgId) + " by " + execContext.getMyAccount() );
            return;
        }
        String reblogOid = MyQuery.idToOid(OidEnum.REBLOG_OID, msgId, actorId);
        try {
            if (!execContext.getMyAccount().getConnection().destroyReblog(reblogOid)) {
                logExecutionError(false, "Connection returned 'false' " + method
                        + MyQuery.msgInfoForLog(msgId));
            }
        } catch (ConnectionException e) {
            // "Not found" means that there is no such "Status", so we may
            // assume that it's Ok!
            if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                logConnectionException(e, method + "; reblogOid:" + reblogOid + ", " + MyQuery.msgInfoForLog(msgId));
            }
        }
        if (noErrors()) {
            try {
                // And delete the reblog from local storage
                MyProvider.deleteActivity(execContext.getMyContext(), reblogAndType.first, msgId, false);
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
                AActivity activity = execContext.getMyAccount().getConnection().getMessage(oid);
                if (activity.isEmpty()) {
                    logExecutionError(false, "Received Message is empty, " + MyQuery.msgInfoForLog(msgId));
                } else {
                    try {
                        new DataUpdater(execContext).onActivity(activity);
                    } catch (Exception e) {
                        logExecutionError(false, "Error while saving to the local cache,"
                                + MyQuery.msgInfoForLog(msgId) + ", " + e.getMessage());
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
        AActivity activity = null;
        String status = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        String oid = getMsgOid(method, msgId, false);
        TriState isPrivate = MyQuery.msgIdToTriState(MsgTable.PRIVATE, msgId);
        Audience recipients = Audience.fromMsgId(execContext.getMyAccount().getOrigin(), msgId);
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
            if (recipients.isEmpty() || isPrivate != TriState.TRUE) {
                long replyToMsgId = MyQuery.msgIdToLongColumnValue(
                        MsgTable.IN_REPLY_TO_MSG_ID, msgId);
                String replyToMsgOid = getMsgOid(method, replyToMsgId, false);
                activity = execContext.getMyAccount().getConnection()
                        .updateStatus(status.trim(), oid, replyToMsgOid, mediaUri);
            } else {
                String recipientOid = getActorOid(method, recipients.getFirst().actorId, true);
                // Currently we don't use Screen Name, I guess id is enough.
                activity = execContext.getMyAccount().getConnection()
                        .postPrivateMessage(status.trim(), oid, recipientOid, mediaUri);
            }
            logIfEmptyMessage(method, msgId, activity.getMessage());
        } catch (ConnectionException e) {
            logConnectionException(e, method + "; " + msgLog);
        }
        if (noErrors() && activity != null) {
            // The message was sent successfully, so now update unsent message
            // New User's message should be put into the user's Home timeline.
            activity.getMessage().msgId = msgId;
            new DataUpdater(execContext).onActivity(activity);
            execContext.getResult().setItemId(msgId);
        } else {
            execContext.getMyContext().getNotifier().onUnsentMessage(msgId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfEmptyMessage(String method, long msgId, Note message) {
        if (message == null || message.isEmpty()) {
            logExecutionError(false, method + "; Received Message is empty, " + MyQuery.msgInfoForLog(msgId));
        }
    }

    private void reblog(long rebloggedMessageId) {
        final String method = "Reblog";
        String oid = getMsgOid(method, rebloggedMessageId, true);
        AActivity activity = AActivity.EMPTY;
        if (noErrors()) {
            try {
                activity = execContext.getMyAccount().getConnection().postReblog(oid);
                logIfEmptyMessage(method, rebloggedMessageId, activity.getMessage());
            } catch (ConnectionException e) {
                logConnectionException(e, "Reblog " + oid);
            }
        }
        if (noErrors()) {
            // The tweet was sent successfully
            // Reblog should be put into the user's Home timeline!
            new DataUpdater(execContext).onActivity(activity);
            MyProvider.updateMessageReblogged(execContext.getMyContext(), activity.accountActor.origin, rebloggedMessageId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }
    
    private void rateLimitStatus() {
        try {
            RateLimitStatus rateLimitStatus = execContext.getMyAccount().getConnection().rateLimitStatus();
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
