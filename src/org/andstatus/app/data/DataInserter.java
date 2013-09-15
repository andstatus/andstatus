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

import java.util.Date;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Stores ("inserts" -  adds or updates) messages and users
 *  from Microblogging system in the database.
 * 
 * @author Yuri Volkov
 */
public class DataInserter {
    private static final String TAG = DataInserter.class.getSimpleName();

    private ContentResolver mContentResolver;

    private Context mContext;

    /**
     *  New messages counter. These may be "general" or Direct messages...
     */
    private int mMessages;

    /**
     * Number of new Mentions received 
     */
    private int mMentions;
    /**
     * Number of new Replies received 
     */
    private int mReplies;
    /**
     * Total number of messages downloaded
     */
    private int mDownloaded;

    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    
    public DataInserter(MyAccount ma_in, Context context, TimelineTypeEnum timelineType) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        ma = ma_in;
        mTimelineType = timelineType;
    }
    
    public long insertOrUpdateMsg(MbMessage message, LatestUserMessages lum) throws SQLiteConstraintException {
        return insertOrUpdateMsgBySender(message, lum, 0);
    }
    
    private long insertOrUpdateMsgBySender(MbMessage message, LatestUserMessages lum, long senderId_in) throws SQLiteConstraintException {
        final String funcName = "Inserting/updating msg";
        /**
         * Id of the message in our system, see {@link MyDatabase.Msg#MSG_ID}
         */
        Long rowId = 0L;
        try {
            if (message.isEmpty()) {
                Log.w(TAG, funcName +", the message is empty, skipping: " + message.toString());
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
                mDownloaded += 1;
            }
            
            // Sender
            Long senderId = 0L;
            if (message.sender != null) {
                senderId = insertOrUpdateUser(message.sender, lum);
            } else if (senderId_in != 0) {
                senderId = senderId_in;
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

                if (senderId !=0 && ma.getUserId() == senderId) {
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
                Log.w(TAG, funcName +": no message id");
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
                rowId = MyProvider.oidToId(OidEnum.MSG_OID, ma.getOriginId(), rowOid);
                // Construct the Uri to the Msg
                Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), mTimelineType, false, rowId);

                long sentDate_stored = 0;
                if (rowId != 0) {
                    sentDate_stored = MyProvider.msgIdToLongColumnValue(Msg.SENT_DATE, rowId);
                    isNew = (sentDate_stored == 0);
                    if (!isNew) {
                      long senderId_stored = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, rowId);
                      isNew = (senderId_stored == 0);
                    }
                }
                if (sentDate > sentDate_stored) {
                    isNewer = true;
                    // This message is newer than already stored in our database, so count it!
                    countIt = true;
                }
                
                String body = message.body;

                if (isNew) {
                    values.put(MyDatabase.Msg.CREATED_DATE, createdDate);
                    
                    if (senderId != 0) {
                        // Store the Sender only for the first retrieved message.
                        // Don't overwrite the original sender (especially the first reblogger) 
                        values.put(MyDatabase.Msg.SENDER_ID, senderId);
                    }

                    values.put(MyDatabase.Msg.MSG_OID, rowOid);
                    values.put(MyDatabase.Msg.ORIGIN_ID, ma.getOriginId());
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

                boolean mentioned = (mTimelineType == TimelineTypeEnum.MENTIONS);
                
                switch (mTimelineType) {
                    case DIRECT:
                        values.put(MyDatabase.MsgOfUser.DIRECTED, 1);
                        Long recipientId = 0L;
                        if (message.recipient != null) {
                            recipientId = insertOrUpdateUser(message.recipient, lum);
                        }
                        values.put(MyDatabase.Msg.RECIPIENT_ID, recipientId);
                        break;
                        
                    case HOME:
                        values.put(MyDatabase.MsgOfUser.SUBSCRIBED, 1);
                        
                    default:
                        if (!TextUtils.isEmpty(message.via)) {
                            values.put(MyDatabase.Msg.VIA, message.via);
                        }
                        if (message.favoritedByReader != null) {
                            values.put(MyDatabase.MsgOfUser.FAVORITED, SharedPreferencesUtil.isTrue(message.favoritedByReader));
                        }

                        if (message.inReplyToMessage != null) {
                            // Type of the timeline is ALL meaning that message does not belong to this timeline
                            DataInserter di = new DataInserter(ma, mContext, MyDatabase.TimelineTypeEnum.ALL);
                            inReplyToMessageId = di.insertOrUpdateMsg(message.inReplyToMessage, lum);
                            if (message.inReplyToMessage.sender != null) {
                                inReplyToUserId = MyProvider.oidToId(OidEnum.USER_OID, message.originId, message.inReplyToMessage.sender.oid);
                            } else if (inReplyToMessageId != 0) {
                                inReplyToUserId = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, inReplyToMessageId);
                            }
                        }
                        if (inReplyToUserId != 0) {
                            values.put(MyDatabase.Msg.IN_REPLY_TO_USER_ID, inReplyToUserId);

                            if (ma.getUserId() == inReplyToUserId) {
                                values.put(MyDatabase.MsgOfUser.REPLIED, 1);
                                if (countIt) { 
                                    mReplies++; 
                                    }
                                // We consider a Reply to be a Mention also?! 
                                // ...Yes, at least as long as we don't have "Replies" timeline type 
                                mentioned = true;
                            }
                        }
                        if (inReplyToMessageId != 0) {
                            values.put(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
                        }
                }
                
                if (countIt) { 
                    mMessages++;
                    }
                if (body.length() > 0) {
                    if (!mentioned) {
                        // Check if current user was mentioned in the text of the message
                        if (body.length() > 0) {
                            if (body.contains("@" + ma.getUsername())) {
                                mentioned = true;
                            }
                        }
                    }
                }
                if (mentioned) {
                    if (countIt) { 
                        mMentions++;
                        }
                  values.put(MyDatabase.MsgOfUser.MENTIONED, 1);
                }
                
                if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, ((rowId==0) ? "insertMsg" : "updateMsg") 
                            + ":" 
                            + (isNew ? " new;" : "") 
                            + (isNewer ? " newer, sent at " + new Date(sentDate).toString() + ";" : "") );
                }
                if (rowId == 0) {
                    // There was no such row so add the new one
                    msgUri = mContentResolver.insert(MyProvider.getTimelineUri(ma.getUserId(), MyDatabase.TimelineTypeEnum.HOME, false), values);
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
                Log.w(TAG, funcName +": the message was skipped: " + message.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, funcName +": " + e.toString());
            e.printStackTrace();
        }

        return rowId;
    }

    public long insertOrUpdateUser(MbUser user) throws SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long userId = insertOrUpdateUser(user, lum);
        lum.save();
        return userId;
    }
    
    public long insertOrUpdateUser(MbUser sender, LatestUserMessages lum) throws SQLiteConstraintException {
        String userName = sender.userName;
        String rowOid = sender.oid;
        Long originId = sender.originId;
        Long rowId = 0L;
        if (!SharedPreferencesUtil.isEmpty(rowOid)) {
            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(OidEnum.USER_OID, originId, rowOid);
        }
        if (rowId == 0) {
            // Try to Lookup by Username
            if (SharedPreferencesUtil.isEmpty(userName)) {
                Log.w(TAG, "insertUserFromJSONObject - no username: " + sender.toString());
                return rowId;
            } else {
                rowId = MyProvider.userNameToId(originId, userName);
            }
        }
        
        try {
            ContentValues values = new ContentValues();

            if (rowOid.length()>0) {
                values.put(MyDatabase.User.USER_OID, rowOid);
            }
            values.put(MyDatabase.User.ORIGIN_ID, originId);
            if (!SharedPreferencesUtil.isEmpty(userName)) {
                values.put(MyDatabase.User.USERNAME, userName);
            }
            if (!TextUtils.isEmpty(sender.realName)) {
                values.put(MyDatabase.User.REAL_NAME, sender.realName);
            }
            if (!TextUtils.isEmpty(sender.avatarUrl)) {
                values.put(MyDatabase.User.AVATAR_URL, sender.avatarUrl);
            }
            if (!TextUtils.isEmpty(sender.description)) {
                values.put(MyDatabase.User.DESCRIPTION, sender.description);
            }
            if (!TextUtils.isEmpty(sender.homepage)) {
                values.put(MyDatabase.User.HOMEPAGE, sender.homepage);
            }
            if (sender.createdDate > 0) {
                values.put(MyDatabase.User.CREATED_DATE, sender.createdDate);
            } else if ( rowId == 0) {
                if (sender.updatedDate > 0) {
                    values.put(MyDatabase.User.CREATED_DATE, sender.updatedDate);
                }
            }
            if (sender.followedByReader != null ) {
                values.put(MyDatabase.FollowingUser.USER_FOLLOWED, sender.followedByReader);
            }
            
            // Construct the Uri to the User
            Uri userUri = MyProvider.getUserUri(ma.getUserId(), rowId);
            if (rowId == 0) {
                // There was no such row so add new one
                userUri = mContentResolver.insert(userUri, values);
                rowId = MyProvider.uriToUserId(userUri);
            } else {
              mContentResolver.update(userUri, values, null, null);
            }
            if (sender.latestMessage != null) {
                // This message doesn't have a sender!
                insertOrUpdateMsgBySender(sender.latestMessage, lum, rowId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "insertUserFromJSONObject: " + e.toString());
        }
        return rowId;
    }
    
    public long insertOrUpdateMsg(MbMessage jo) throws SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long rowId = insertOrUpdateMsg(jo, lum);
        lum.save();
        mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
        return rowId;
    }

    public int newMessagesCount() {
        return mMessages;
    }

    public int newRepliesCount() {
        return mReplies;
    }

    public int newMentionsCount() {
        return mMentions;
    }

    public int totalMessagesDownloadedCount() {
        return mDownloaded;
    }
}
