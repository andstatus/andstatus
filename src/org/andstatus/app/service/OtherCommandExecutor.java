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
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AvatarLoader;
import org.andstatus.app.data.DataInserter;
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
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

public class OtherCommandExecutor implements OneCommandExecutor, OneCommandExecutorParent{
    private OneCommandExecutorParent parent;

    private CommandData commandData;
    private Context context;

    public OtherCommandExecutor(CommandData commandData) {
        this.commandData = commandData;
        context = MyContextHolder.get().context();
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
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
                createOrDestroyFavorite(commandData,
                        commandData.itemId, 
                        commandData.getCommand() == CommandEnum.CREATE_FAVORITE);
                break;
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
                followOrStopFollowingUser(commandData,
                        commandData.itemId, 
                        commandData.getCommand() == CommandEnum.FOLLOW_USER);
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
                commandData.getResult().incrementParseExceptions();
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
            commandData.getResult().incrementParseExceptions();
        } else {
            commandData.getResult().incrementNumIoExceptions();
        }
        MyLog.e(this, detailedMessage + ": " + e.toString());
    }

    private void rateLimitStatus(CommandData commandData) {
        if (setErrorIfCredentialsNotVerified(commandData, commandData.getAccount())) {
            return;
        }
        boolean ok = false;
        try {
            MbRateLimitStatus rateLimitStatus = commandData.getAccount().getConnection().rateLimitStatus();
            ok = !rateLimitStatus.isEmpty();
            if (ok) {
                commandData.getResult().setRemainingHits(rateLimitStatus.remaining); 
                commandData.getResult().setHourlyLimit(rateLimitStatus.limit);
             }
        } catch (ConnectionException e) {
            logConnectionException(e, commandData, "rateLimitStatus Exception");
        }
        setSoftErrorIfNotOk(commandData, ok);
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

    @Override
    public boolean isStopping() {
        if (parent != null) {
            return parent.isStopping();
        } else {
            return false;
        }
    }
}
