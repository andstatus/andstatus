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

import android.content.ContentValues;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandExecutionContext;
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
    private CommandExecutionContext execContext;

    public DataInserter(MyAccount ma) {
        this(new CommandExecutionContext(CommandData.getEmpty(), ma));
    }
    
    public DataInserter(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }
    
    public long insertOrUpdateMsg(MbMessage message, LatestUserMessages lum) {
        return insertOrUpdateMsgBySender(message, lum, 0);
    }
    
    private long insertOrUpdateMsgBySender(MbMessage messageIn, LatestUserMessages lum, long senderIdIn) {
        final String funcName = "Inserting/updating msg";
        /**
         * Id of the message in our system, see {@link MyDatabase.Msg#MSG_ID}
         */
        Long rowId = 0L;
        try {
            if (messageIn.isEmpty()) {
                MyLog.w(TAG, funcName +", the message is empty, skipping: " + messageIn.toString());
                return 0;
            }
            
            MbMessage message = messageIn;
            ContentValues values = new ContentValues();

            // We use Created date from this message as "Sent date" even for reblogs in order to
            // get natural order of the tweets.
            // Otherwise reblogged message may appear as old
            long sentDate = message.sentDate;
            long createdDate = 0;
            if (sentDate > 0) {
                createdDate = sentDate;
                execContext.getResult().incrementDownloadedCount();
            }
            
            long actorId = 0L;
            if (message.actor != null) {
                actorId = insertOrUpdateUser(message.actor, lum);
            } else {
                actorId = execContext.getMyAccount().getUserId();
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

                if (senderId !=0 && execContext.getMyAccount().getUserId() == senderId) {
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

            /**
             * Is the row first time retrieved?
             * Actually we count a message as "New" also in a case
             *  there was only "a stub" stored (without a sent date and a body)
             */
            boolean isNew = true;

            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(OidEnum.MSG_OID, execContext.getMyAccount().getOriginId(), rowOid);
            // Construct the Uri to the Msg
            Uri msgUri = MyProvider.getTimelineMsgUri(execContext.getMyAccount().getUserId(), execContext.getTimelineType(), false, rowId);

            long sentDateStored = 0;
            if (rowId != 0) {
                sentDateStored = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, rowId);
                isNew = (sentDateStored == 0);
                if (!isNew) {
                  long senderIdStored = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, rowId);
                  isNew = (senderIdStored == 0);
                }
            }

            if (isNew) {
                values.put(MyDatabase.Msg.CREATED_DATE, createdDate);
                
                if (senderId != 0) {
                    // Store the Sender only for the first retrieved message.
                    // Don't overwrite the original sender (especially the first reblogger) 
                    values.put(MyDatabase.Msg.SENDER_ID, senderId);
                }

                values.put(MyDatabase.Msg.MSG_OID, rowOid);
                values.put(MyDatabase.Msg.ORIGIN_ID, execContext.getMyAccount().getOriginId());
                values.put(MyDatabase.Msg.BODY, message.getBody());
            }
            
            /**
             * Is the message newer than stored in the database (e.g. the newer reblog of existing message)
             */
            boolean isNewerThanInDatabase = sentDate > sentDateStored;
            if (isNewerThanInDatabase) {
                // Remember the latest sent date in order to see the reblogged message 
                // at the top of the sorted list 
                values.put(MyDatabase.Msg.SENT_DATE, sentDate);
            }
            
            if (message.recipient != null) {
                long recipientId = insertOrUpdateUser(message.recipient, lum);
                values.put(MyDatabase.Msg.RECIPIENT_ID, recipientId);
                if (recipientId == execContext.getMyAccount().getUserId()) {
                    values.put(MyDatabase.MsgOfUser.DIRECTED, 1);
                    MyLog.v(this, "Message '" + message.oid + "' is Directed to " 
                            + execContext.getMyAccount().getAccountName() );
                }
            }
            if (execContext.getTimelineType() == TimelineTypeEnum.HOME) {
                values.put(MyDatabase.MsgOfUser.SUBSCRIBED, 1);
            }
            if (!TextUtils.isEmpty(message.via)) {
                values.put(MyDatabase.Msg.VIA, message.via);
            }
            if (!TextUtils.isEmpty(message.url)) {
                values.put(MyDatabase.Msg.URL, message.url);
            }
            if (message.isPublic()) {
                values.put(MyDatabase.Msg.PUBLIC, 1);
            }

            if (message.favoritedByActor != TriState.UNKNOWN
                    && actorId != 0
                    && actorId == execContext.getMyAccount().getUserId()) {
                values.put(MyDatabase.MsgOfUser.FAVORITED,
                        message.favoritedByActor.toBoolean(false));
                MyLog.v(this,
                        "Message '"
                                + message.oid
                                + "' "
                                + (message.favoritedByActor.toBoolean(false) ? "favorited"
                                        : "unfavorited")
                                + " by " + execContext.getMyAccount().getAccountName());
            }

            boolean mentioned = isMentionedAndPutInReplyToMessage(message, lum, values);
            
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(this, ((rowId==0) ? "insertMsg" : "updateMsg") 
                        + ":" 
                        + (isNew ? " new;" : "") 
                        + (isNewerThanInDatabase ? " newer, sent at " + new Date(sentDate).toString() + ";" : "") );
            }
            
            if (MyContextHolder.get().isTestRun()) {
                MyContextHolder.get().put(new AssersionData("insertOrUpdateMsg", values));
            }
            if (rowId == 0) {
                // There was no such row so add the new one
                msgUri = execContext.getContext().getContentResolver().insert(MyProvider.getTimelineUri(execContext.getMyAccount().getUserId(), execContext.getTimelineType(), false), values);
                rowId = MyProvider.uriToMessageId(msgUri);
            } else {
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
            }

            if (isNew) {
                for (MbAttachment attachment : message.attachments) {
                    DownloadData dd = DownloadData.newForMessage(rowId, attachment.contentType, attachment.getUrl());
                    dd.saveToDatabase();
                    if (attachment.contentType == MyContentType.IMAGE && MyPreferences.showAttachedImages()) {
                        dd.requestDownload();
                    }
                }
            }
            
            if (isNewerThanInDatabase) {
                // This message is newer than already stored in our database, so count it!
                execContext.getResult().incrementMessagesCount(execContext.getTimelineType());
                if (mentioned) {
                    execContext.getResult().incrementMentionsCount();
                }
            }
            if (senderId != 0) {
                // Remember all messages that we added or updated
                lum.onNewUserMsg(new UserMsg(senderId, rowId, sentDate));
            }
            if ( authorId != 0 && authorId != senderId ) {
                lum.onNewUserMsg(new UserMsg(authorId, rowId, createdDate));
            }
        } catch (Exception e) {
            MyLog.e(this, funcName, e);
        }

        return rowId;
    }

    private boolean isMentionedAndPutInReplyToMessage(MbMessage message, LatestUserMessages lum, ContentValues values) {
        boolean mentioned = execContext.getTimelineType() == TimelineTypeEnum.MENTIONS;
        if (message.inReplyToMessage != null) {
            // If the Msg is a Reply to another message
            Long inReplyToMessageId = 0L;
            Long inReplyToUserId = 0L;
            // Type of the timeline is ALL meaning that message does not belong to this timeline
            DataInserter di = new DataInserter(execContext);
            inReplyToMessageId = di.insertOrUpdateMsg(message.inReplyToMessage, lum);
            if (message.inReplyToMessage.sender != null) {
                inReplyToUserId = MyProvider.oidToId(OidEnum.USER_OID, message.originId, message.inReplyToMessage.sender.oid);
            } else if (inReplyToMessageId != 0) {
                inReplyToUserId = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, inReplyToMessageId);
            }
            if (inReplyToMessageId != 0) {
                values.put(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
            }
            if (inReplyToUserId != 0) {
                values.put(MyDatabase.Msg.IN_REPLY_TO_USER_ID, inReplyToUserId);

                if (execContext.getMyAccount().getUserId() == inReplyToUserId) {
                    values.put(MyDatabase.MsgOfUser.REPLIED, 1);
                    // We count replies as Mentions 
                    mentioned = true;
                }
            }
        }
        // Check if current user was mentioned in the text of the message
        if (message.getBody().length() > 0 
                && !mentioned 
                && message.getBody().contains("@" + execContext.getMyAccount().getUsername())) {
            mentioned = true;
        }
        if (mentioned) {
            values.put(MyDatabase.MsgOfUser.MENTIONED, 1);
        }
        return mentioned;
    }

    public long insertOrUpdateUser(MbUser user) {
        LatestUserMessages lum = new LatestUserMessages();
        long userId = insertOrUpdateUser(user, lum);
        lum.save();
        return userId;
    }
    
    /**
     * @return userId
     */
    public long insertOrUpdateUser(MbUser mbUser, LatestUserMessages lum) {
        if (mbUser.isEmpty()) {
            MyLog.v(this, "insertUser - mbUser is empty");
            return 0;
        }
        
        String userOid = mbUser.oid;
        long originId = mbUser.originId;
        long userId = 0L;
        if (!SharedPreferencesUtil.isEmpty(userOid)) {
            // Lookup the System's (AndStatus) id from the Originated system's id
            userId = MyProvider.oidToId(OidEnum.USER_OID, originId, userOid);
        }
        try {
            ContentValues values = new ContentValues();

            String userName = mbUser.getUserName();
            if (userId == 0 && SharedPreferencesUtil.isEmpty(userName)) {
                userName = "id:" + userOid;
            }
            if (!SharedPreferencesUtil.isEmpty(userName)) {
                values.put(MyDatabase.User.USERNAME, userName);
            }

            String webFingerId = mbUser.getWebFingerId();
            if (userId == 0 && SharedPreferencesUtil.isEmpty(webFingerId)) {
                webFingerId = userName;
            }
            if (!SharedPreferencesUtil.isEmpty(webFingerId)) {
                values.put(MyDatabase.User.WEBFINGER_ID, webFingerId);
            }
            
            String realName = mbUser.realName;
            if (userId == 0 && SharedPreferencesUtil.isEmpty(realName)) {
                realName = userName;
            }
            if (!SharedPreferencesUtil.isEmpty(realName)) {
                values.put(MyDatabase.User.REAL_NAME, realName);
            }
            
            if (!SharedPreferencesUtil.isEmpty(mbUser.avatarUrl)) {
                values.put(MyDatabase.User.AVATAR_URL, mbUser.avatarUrl);
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.description)) {
                values.put(MyDatabase.User.DESCRIPTION, mbUser.description);
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.homepage)) {
                values.put(MyDatabase.User.HOMEPAGE, mbUser.homepage);
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.getUrl())) {
                values.put(MyDatabase.User.URL, mbUser.getUrl());
            }
            if (mbUser.createdDate > 0) {
                values.put(MyDatabase.User.CREATED_DATE, mbUser.createdDate);
            } else if ( userId == 0 && mbUser.updatedDate > 0) {
                values.put(MyDatabase.User.CREATED_DATE, mbUser.updatedDate);
            }
            
            long readerId = 0L;
            if (mbUser.actor != null) {
                readerId = insertOrUpdateUser(mbUser.actor, lum);
            } else {
                readerId = execContext.getMyAccount().getUserId();
            }
            if (mbUser.followedByActor != TriState.UNKNOWN
                    && readerId == execContext.getMyAccount().getUserId()) {
                values.put(MyDatabase.FollowingUser.USER_FOLLOWED,
                        mbUser.followedByActor.toBoolean(false));
                MyLog.v(this,
                        "User '" + userName + "' is "
                                + (mbUser.followedByActor.toBoolean(false) ? "" : "not ")
                                + "followed by " + execContext.getMyAccount().getAccountName());
            }
            
            // Construct the Uri to the User
            Uri userUri = MyProvider.getUserUri(execContext.getMyAccount().getUserId(), userId);
            if (userId == 0) {
                // There was no such row so add new one
                
                values.put(MyDatabase.User.USER_OID, userOid);
                values.put(MyDatabase.User.ORIGIN_ID, originId);
                userUri = execContext.getContext().getContentResolver().insert(userUri, values);
                userId = MyProvider.uriToUserId(userUri);
            } else if (values.size() > 0) {
                execContext.getContext().getContentResolver().update(userUri, values, null, null);
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
    
    public long insertOrUpdateMsg(MbMessage message) {
        LatestUserMessages lum = new LatestUserMessages();
        long rowId = insertOrUpdateMsg(message, lum);
        lum.save();
        execContext.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
        return rowId;
    }
}
