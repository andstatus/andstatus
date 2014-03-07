/* 
 * Copyright (c) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.service.CommandExecutionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Date;

/**
 * Stores ("inserts" -  adds or updates) messages and users
 *  from Microblogging system in the database.
 * 
 * @author yvolk@yurivolkov.com
 */
public class DataInserter {
    private static final String TAG = DataInserter.class.getSimpleName();

    private ContentResolver mContentResolver;
    private CommandExecutionData counters;

    public DataInserter(MyAccount ma, Context context) {
        this(new CommandExecutionData(ma, context));
    }
    
    public DataInserter(CommandExecutionData counters) {
        this.counters = counters;
        mContentResolver = counters.getContext().getContentResolver();
    }
    
    public long insertOrUpdateMsg(MbMessage message, LatestUserMessages lum) throws SQLiteConstraintException {
        return insertOrUpdateMsgBySender(message, lum, 0);
    }
    
    private long insertOrUpdateMsgBySender(MbMessage message, LatestUserMessages lum, long senderIdIn) throws SQLiteConstraintException {
        final String funcName = "Inserting/updating msg";
        /**
         * Id of the message in our system, see {@link MyDatabase.Msg#MSG_ID}
         */
        Long rowId = 0L;
        try {
            if (message.isEmpty()) {
                MyLog.w(TAG, funcName +", the message is empty, skipping: " + message.toString());
                return 0;
            }
            
            /**
             * Don't insert this message
             */
            boolean skipIt = false;
            ContentValues values = new ContentValues();

            // We use Created date from this message as "Sent date" even for reblogs in order to
            // get natural order of the tweets.
            // Otherwise reblogged message may appear as old
            long sentDate = message.sentDate;
            long createdDate = 0;
            if (sentDate > 0) {
                createdDate = sentDate;
                counters.incrementDownloadedCount();
            }
            
            long actorId = 0L;
            if (message.actor != null) {
                actorId = insertOrUpdateUser(message.actor, lum);
            } else {
                actorId = counters.getMyAccount().getUserId();
            }
            
            // Sender
            long senderId = 0L;
            if (message.sender != null) {
                senderId = insertOrUpdateUser(message.sender, lum);
            } else if (senderIdIn != 0) {
                senderId = senderIdIn;
            }

            String rowOid = message.oid;
            
            // Author
            long authorId = senderId;
            // Is this a reblog?
            if (message.rebloggedMessage != null) {
                if (message.rebloggedMessage.sender != null) {
                    // Author of that message
                    authorId = insertOrUpdateUser(message.rebloggedMessage.sender, lum);
                }

                if (senderId !=0 && counters.getMyAccount().getUserId() == senderId) {
                    // Msg was reblogged by current User (he is the Sender)
                    values.put(MyDatabase.MsgOfUser.REBLOGGED, 1);

                    // Remember original id of the reblog message
                    // We will need it to "undo reblog" for our reblog
                    if (!SharedPreferencesUtil.isEmpty(rowOid)) {
                        values.put(MyDatabase.MsgOfUser.REBLOG_OID, rowOid);
                    }
                }

                // And replace reblog with original message!
                // So we won't have lots of reblogs but rather one original message
                message = message.rebloggedMessage;
                // Try to retrieve the message id again
                if (!TextUtils.isEmpty(message.oid)) {
                    rowOid = message.oid;
                } 
                // Created date is usually earlier for reblogs:
                if (message.sentDate > 0 ) {
                    createdDate = message.sentDate;
                }
            }
            if (authorId != 0) {
                values.put(MyDatabase.Msg.AUTHOR_ID, authorId);
            }

            if (SharedPreferencesUtil.isEmpty(rowOid)) {
                MyLog.w(TAG, funcName +": no message id");
                skipIt = true;
            }
            if (!skipIt) {
                /**
                 * Is the row first time retrieved?
                 * Actually we count a message as "New" also in a case
                 *  there was only "a stub" stored (without a sent date and a body)
                 */
                boolean isNew = true;
                /**
                 * Is the message newer than stored in the database (e.g. the newer reblog of existing message)
                 */
                boolean isNewer = false;
                /**
                 * Count this message. 
                 */
                boolean countIt = false;

                // Lookup the System's (AndStatus) id from the Originated system's id
                rowId = MyProvider.oidToId(OidEnum.MSG_OID, counters.getMyAccount().getOriginId(), rowOid);
                // Construct the Uri to the Msg
                Uri msgUri = MyProvider.getTimelineMsgUri(counters.getMyAccount().getUserId(), counters.getTimelineType(), false, rowId);

                long sentDateStored = 0;
                if (rowId != 0) {
                    sentDateStored = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, rowId);
                    isNew = (sentDateStored == 0);
                    if (!isNew) {
                      long senderIdStored = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, rowId);
                      isNew = (senderIdStored == 0);
                    }
                }
                if (sentDate > sentDateStored) {
                    isNewer = true;
                    // This message is newer than already stored in our database, so count it!
                    countIt = true;
                }
                
                String body = message.getBody();

                if (isNew) {
                    values.put(MyDatabase.Msg.CREATED_DATE, createdDate);
                    
                    if (senderId != 0) {
                        // Store the Sender only for the first retrieved message.
                        // Don't overwrite the original sender (especially the first reblogger) 
                        values.put(MyDatabase.Msg.SENDER_ID, senderId);
                    }

                    values.put(MyDatabase.Msg.MSG_OID, rowOid);
                    values.put(MyDatabase.Msg.ORIGIN_ID, counters.getMyAccount().getOriginId());
                    values.put(MyDatabase.Msg.BODY, body);
                }
                if (isNewer) {
                    // Remember the latest sent date in order to see the reblogged message 
                    // at the top of the sorted list 
                    values.put(MyDatabase.Msg.SENT_DATE, sentDate);
                }
                
                // If the Msg is a Reply to other message
                Long inReplyToUserId = 0L;
                Long inReplyToMessageId = 0L;

                if (message.recipient != null) {
                    long recipientId = insertOrUpdateUser(message.recipient, lum);
                    values.put(MyDatabase.Msg.RECIPIENT_ID, recipientId);
                    if (recipientId == counters.getMyAccount().getUserId()) {
                        values.put(MyDatabase.MsgOfUser.DIRECTED, 1);
                        MyLog.v(this, "Message '" + message.oid + "' is Directed to " 
                                + counters.getMyAccount().getAccountName() );
                    }
                }
                boolean mentioned = counters.getTimelineType() == TimelineTypeEnum.MENTIONS;
                if (counters.getTimelineType() == TimelineTypeEnum.HOME) {
                    values.put(MyDatabase.MsgOfUser.SUBSCRIBED, 1);
                }
                if (!TextUtils.isEmpty(message.via)) {
                    values.put(MyDatabase.Msg.VIA, message.via);
                }
                if (!TextUtils.isEmpty(message.url)) {
                    values.put(MyDatabase.Msg.URL, message.url);
                }
                if (message.favoritedByActor != TriState.UNKNOWN
                        && actorId != 0
                        && actorId == counters.getMyAccount().getUserId()) {
                    values.put(MyDatabase.MsgOfUser.FAVORITED,
                            SharedPreferencesUtil.isTrue(message.favoritedByActor));
                    MyLog.v(this,
                            "Message '"
                                    + message.oid
                                    + "' "
                                    + (message.favoritedByActor.toBoolean(false) ? "favorited"
                                            : "unfavorited")
                                    + " by " + counters.getMyAccount().getAccountName());
                }

                if (message.inReplyToMessage != null) {
                    // Type of the timeline is ALL meaning that message does not belong to this timeline
                    DataInserter di = new DataInserter(counters);
                    inReplyToMessageId = di.insertOrUpdateMsg(message.inReplyToMessage, lum);
                    if (message.inReplyToMessage.sender != null) {
                        inReplyToUserId = MyProvider.oidToId(OidEnum.USER_OID, message.originId, message.inReplyToMessage.sender.oid);
                    } else if (inReplyToMessageId != 0) {
                        inReplyToUserId = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, inReplyToMessageId);
                    }
                }
                if (inReplyToUserId != 0) {
                    values.put(MyDatabase.Msg.IN_REPLY_TO_USER_ID, inReplyToUserId);

                    if (counters.getMyAccount().getUserId() == inReplyToUserId) {
                        values.put(MyDatabase.MsgOfUser.REPLIED, 1);
                        // We count replies as Mentions 
                        mentioned = true;
                    }
                }
                if (inReplyToMessageId != 0) {
                    values.put(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
                }
                if (message.isPublic()) {
                    values.put(MyDatabase.Msg.PUBLIC, 1);
                }
                
                if (countIt) { 
                    counters.incrementMessagesCount();
                    }
                // Check if current user was mentioned in the text of the message
                if (body.length() > 0 
                        && !mentioned 
                        && body.contains("@" + counters.getMyAccount().getUsername())) {
                    mentioned = true;
                }
                if (mentioned) {
                    if (countIt) { 
                        counters.incrementMentionsCount();
                        }
                  values.put(MyDatabase.MsgOfUser.MENTIONED, 1);
                }
                
                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    MyLog.v(TAG, ((rowId==0) ? "insertMsg" : "updateMsg") 
                            + ":" 
                            + (isNew ? " new;" : "") 
                            + (isNewer ? " newer, sent at " + new Date(sentDate).toString() + ";" : "") );
                }
                if (MyContextHolder.get().isTestRun()) {
                    MyContextHolder.get().put(new AssersionData("insertOrUpdateMsg", values));
                }
                if (rowId == 0) {
                    // There was no such row so add the new one
                    msgUri = mContentResolver.insert(MyProvider.getTimelineUri(counters.getMyAccount().getUserId(), TimelineTypeEnum.HOME, false), values);
                    rowId = MyProvider.uriToMessageId(msgUri);
                } else {
                  mContentResolver.update(msgUri, values, null, null);
                }
                
                if (senderId != 0) {
                    // Remember all messages that we added or updated
                    lum.onNewUserMsg(new UserMsg(senderId, rowId, sentDate));
                }
                if ( authorId != 0 && authorId != senderId ) {
                    lum.onNewUserMsg(new UserMsg(authorId, rowId, createdDate));
                }
            }
            if (skipIt) {
                MyLog.w(TAG, funcName +": the message was skipped: " + message.toString());
            }
        } catch (Exception e) {
            MyLog.e(this, funcName, e);
        }

        return rowId;
    }

    public long insertOrUpdateUser(MbUser user) throws SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long userId = insertOrUpdateUser(user, lum);
        lum.save();
        return userId;
    }
    
    /**
     * @return userId
     */
    public long insertOrUpdateUser(MbUser mbUser, LatestUserMessages lum) throws SQLiteConstraintException {
        if (mbUser.isEmpty()) {
            MyLog.v(this, "insertUser - mbUser is empty");
            return 0;
        }
        String userName = mbUser.userName;
        String userOid = mbUser.oid;
        long originId = mbUser.originId;
        
        long readerId = 0L;
        if (mbUser.actor != null) {
            readerId = insertOrUpdateUser(mbUser.actor, lum);
        } else {
            readerId = counters.getMyAccount().getUserId();
        }
        
        long userId = 0L;
        if (!SharedPreferencesUtil.isEmpty(userOid)) {
            // Lookup the System's (AndStatus) id from the Originated system's id
            userId = MyProvider.oidToId(OidEnum.USER_OID, originId, userOid);
        }
        if (userId == 0) {
            // Try to Lookup by Username
            if (SharedPreferencesUtil.isEmpty(userName)) {
                MyLog.w(TAG, "insertUser - no username: " + mbUser.toString());
                return userId;
            } else {
                userId = MyProvider.userNameToId(originId, userName);
            }
        }
        
        try {
            ContentValues values = new ContentValues();

            if (!TextUtils.isEmpty(mbUser.realName)) {
                values.put(MyDatabase.User.REAL_NAME, mbUser.realName);
            }
            if (!TextUtils.isEmpty(mbUser.avatarUrl)) {
                values.put(MyDatabase.User.AVATAR_URL, mbUser.avatarUrl);
            }
            if (!TextUtils.isEmpty(mbUser.description)) {
                values.put(MyDatabase.User.DESCRIPTION, mbUser.description);
            }
            if (!TextUtils.isEmpty(mbUser.homepage)) {
                values.put(MyDatabase.User.HOMEPAGE, mbUser.homepage);
            }
            if (!TextUtils.isEmpty(mbUser.url)) {
                values.put(MyDatabase.User.URL, mbUser.url);
            }
            if (mbUser.createdDate > 0) {
                values.put(MyDatabase.User.CREATED_DATE, mbUser.createdDate);
            } else if ( userId == 0 && mbUser.updatedDate > 0) {
                values.put(MyDatabase.User.CREATED_DATE, mbUser.updatedDate);
            }
            if (mbUser.followedByActor != TriState.UNKNOWN
                    && readerId == counters.getMyAccount().getUserId()) {
                values.put(MyDatabase.FollowingUser.USER_FOLLOWED,
                        mbUser.followedByActor.toBoolean(false));
                MyLog.v(this,
                        "User '" + userName + "' is "
                                + (mbUser.followedByActor.toBoolean(false) ? "" : "not ")
                                + "followed by " + counters.getMyAccount().getAccountName());
            }
            
            // Construct the Uri to the User
            Uri userUri = MyProvider.getUserUri(counters.getMyAccount().getUserId(), userId);
            if (userId == 0) {
                // There was no such row so add new one
                
                if (!TextUtils.isEmpty(userOid)) {
                    values.put(MyDatabase.User.USER_OID, userOid);
                }
                values.put(MyDatabase.User.ORIGIN_ID, originId);
                if (!SharedPreferencesUtil.isEmpty(userName)) {
                    values.put(MyDatabase.User.USERNAME, userName);
                }
                
                userUri = mContentResolver.insert(userUri, values);
                userId = MyProvider.uriToUserId(userUri);
            } else if (values.size() > 0) {
              mContentResolver.update(userUri, values, null, null);
            }
            if (mbUser.latestMessage != null) {
                // This message doesn't have a sender!
                insertOrUpdateMsgBySender(mbUser.latestMessage, lum, userId);
            }
            
        } catch (Exception e) {
            MyLog.e(this, "insertUser exception", e);
        }
        MyLog.v(this, "insertUser, userId=" + userId + "; oid=" + userOid);
        return userId;
    }
    
    public long insertOrUpdateMsg(MbMessage message) throws SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long rowId = insertOrUpdateMsg(message, lum);
        lum.save();
        mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
        return rowId;
    }
}
