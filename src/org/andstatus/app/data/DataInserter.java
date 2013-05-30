package org.andstatus.app.data;


/* 
 * Copyright (c) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.Date;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

/**
 * Stores ("inserts" -  adds or updates) JSON-ed Tweets or Messages
 *  in the database.
 *  The Tweets/Messages come both from process "1" above and from other 
 *  processes ("update status", "favorite/unfavorite", ...).
 *  In also deletes Tweets/Messages from the database.
 * 
 * @author yvolk, torgny.bjers
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

    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    
    public DataInserter(MyAccount ma_in, Context context, TimelineTypeEnum timelineType) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        ma = ma_in;
        mTimelineType = timelineType;
    }
    
    /**
     * Insert a Timeline row from a JSONObject
     * or update existing one.
     * 
     * @param msg - The row to insert
     * @return Information on all messages that were added or updated. Empty Collection if none were added/updated.
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertMsgFromJSONObject(JSONObject msg, LatestUserMessages lum) throws JSONException, SQLiteConstraintException {
        return insertMsgBySender(msg, lum, 0);
    }
    
    /**
     * Insert a Timeline row from a JSONObject
     * or update existing one.
     * 
     * @param msg - The row to insert
     * @param senderId_in - senderId to use in case the message doesn't have the User object
     * @return Information on all messages and their users that were added or updated. 
     *      Empty Collection if none were added/updated. 
     *      Each added message may give up to two entries: one for a Sender and one for an Author.
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    private long insertMsgBySender(JSONObject msg, LatestUserMessages lum, long senderId_in) throws JSONException, SQLiteConstraintException {
        /**
         * Id of the message in our system, see {@link MyDatabase.Msg#MSG_ID}
         */
        Long rowId = 0L;
        try {
            /**
             * Don't insert this message
             */
            boolean skipIt = false;
            ContentValues values = new ContentValues();

            // We use Created date from this message as "Sent date" even for reblogs in order to
            // get natural order of the tweets.
            // Otherwise reblogged message may appear as old
            long sentDate = 0;
            long createdDate = 0;
            if (msg.has("created_at")) {
                Long created = 0L;
                String createdAt = msg.getString("created_at");
                if (createdAt.length() > 0) {
                    created = Date.parse(createdAt);
                }
                if (created > 0) {
                    sentDate = created;
                    createdDate = created;
                }
            }
            
            // Sender
            Long senderId = 0L;
            JSONObject sender;
            String senderObjectName;
            switch (mTimelineType) {
                case DIRECT:
                    senderObjectName = "sender";
                    break;
                default:
                    senderObjectName = "user";
            }
            if (msg.has(senderObjectName)) {
                sender = msg.getJSONObject(senderObjectName);
                senderId = insertUserFromJSONObject(sender, lum);
            } else if (senderId_in != 0) {
                senderId = senderId_in;
            } else {
                Log.w(TAG, "insertMsgBySender: sender object (" + senderObjectName + ") is not present");
                skipIt = true;
            }

            String rowOid = "";
            if (msg.has("id_str")) {
                rowOid = msg.getString("id_str");
            } else if (msg.has("id")) {
                // This is for identi.ca
                rowOid = msg.getString("id");
            } 
            
            // Author
            long authorId = senderId;
            // Is this a reblog?
            if (msg.has("retweeted_status")) {
                JSONObject rebloggedMessage = msg.getJSONObject("retweeted_status");
                if (rebloggedMessage.has("user")) {
                    // Author of that message
                    JSONObject author;
                    author = rebloggedMessage.getJSONObject("user");
                    authorId = insertUserFromJSONObject(author, lum);
                }

                if (ma.getUserId() == senderId) {
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
                msg = rebloggedMessage;

                // Try to retrieve the message id again
                if (msg.has("id_str")) {
                    rowOid = msg.getString("id_str");
                } else if (msg.has("id")) {
                    // This is for identi.ca
                    rowOid = msg.getString("id");
                } 
                
                // Created date is usually earlier for reblogs:
                if (msg.has("created_at")) {
                    Long created = 0L;
                    String createdAt = msg.getString("created_at");
                    if (createdAt.length() > 0) {
                        created = Date.parse(createdAt);
                    }
                    if (created > 0) {
                        createdDate = created;
                    }
                }
            }
            values.put(MyDatabase.Msg.AUTHOR_ID, authorId);

            if (SharedPreferencesUtil.isEmpty(rowOid)) {
                Log.w(TAG, "insertMsgFromJSONObject - no message id");
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
                }
                if (sentDate > sentDate_stored) {
                    isNewer = true;
                    // This message is newer than already stored in our database, so count it!
                    countIt = true;
                }
                
                String body = "";
                if (msg.has("text")) {
                    body = Html.fromHtml(msg.getString("text")).toString();
                }
                values.put(MsgOfUser.TIMELINE_TYPE, mTimelineType.save());

                if (isNew) {
                    values.put(MyDatabase.Msg.CREATED_DATE, createdDate);
                    
                    // Store the Sender only for the first retrieved message.
                    // Don't overwrite the original sender (especially the first reblogger) 
                    values.put(MyDatabase.Msg.SENDER_ID, senderId);

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
                String inReplyToUserOid = "";
                String inReplyToUserName = "";
                Long inReplyToUserId = 0L;
                String inReplyToMessageOid = "";
                Long inReplyToMessageId = 0L;

                boolean mentioned = (mTimelineType == TimelineTypeEnum.MENTIONS);
                
                switch (mTimelineType) {
                    case HOME:
                    case FAVORITES:
                        values.put(MyDatabase.MsgOfUser.SUBSCRIBED, 1);
                    case ALL:
                    case MENTIONS:
                    case USER:
                        if (msg.has("source")) {
                            values.put(MyDatabase.Msg.VIA, msg.getString("source"));
                        }
                        if (msg.has("favorited")) {
                            values.put(MyDatabase.MsgOfUser.FAVORITED, SharedPreferencesUtil.isTrue(msg.getString("favorited")));
                        }

                        if (msg.has("in_reply_to_user_id_str")) {
                            inReplyToUserOid = msg.getString("in_reply_to_user_id_str");
                        } else if (msg.has("in_reply_to_user_id")) {
                            // This is for identi.ca
                            inReplyToUserOid = msg.getString("in_reply_to_user_id");
                        }
                        if (SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                            inReplyToUserOid = "";
                        }
                        if (!SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                            if (msg.has("in_reply_to_screen_name")) {
                                inReplyToUserName = msg.getString("in_reply_to_screen_name");
                            }
                            inReplyToUserId = MyProvider.oidToId(OidEnum.USER_OID, ma.getOriginId(), inReplyToUserOid);
                            
                            // Construct "User" from available info
                            JSONObject inReplyToUser = new JSONObject();
                            inReplyToUser.put("id_str", inReplyToUserOid);
                            inReplyToUser.put("screen_name", inReplyToUserName);
                            if (inReplyToUserId == 0) {
                                inReplyToUserId = insertUserFromJSONObject(inReplyToUser, lum);
                            }
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

                            if (msg.has("in_reply_to_status_id_str")) {
                                inReplyToMessageOid = msg.getString("in_reply_to_status_id_str");
                            } else if (msg.has("in_reply_to_status_id")) {
                                // This is for identi.ca
                                inReplyToMessageOid = msg.getString("in_reply_to_status_id");
                            }
                            if (SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                                inReplyToUserOid = "";
                            }
                            if (!SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                                inReplyToMessageId = MyProvider.oidToId(OidEnum.MSG_OID, ma.getOriginId(), inReplyToMessageOid);
                                if (inReplyToMessageId == 0) {
                                    // Construct Related "Msg" from available info
                                    // and add it recursively
                                    JSONObject inReplyToMessage = new JSONObject();
                                    inReplyToMessage.put("id_str", inReplyToMessageOid);
                                    inReplyToMessage.put(senderObjectName, inReplyToUser);
                                    // Type of the timeline is ALL meaning that message does not belong to this timeline
                                    DataInserter di = new DataInserter(ma, mContext, MyDatabase.TimelineTypeEnum.ALL);
                                    inReplyToMessageId = di.insertMsgFromJSONObject(inReplyToMessage, lum);
                                }
                                values.put(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
                            }
                        }
                        break;
                    case DIRECT:
                        values.put(MyDatabase.MsgOfUser.DIRECTED, 1);

                        // Recipient
                        Long recipientId = 0L;
                        JSONObject recipient = msg.getJSONObject("recipient");
                        recipientId = insertUserFromJSONObject(recipient, lum);
                        values.put(MyDatabase.Msg.RECIPIENT_ID, recipientId);
                        break;
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
                
                // Remember all messages that we added or updated
                lum.onNewUserMsg(new UserMsg(senderId, rowId, sentDate));
                if ( authorId != senderId ) {
                    lum.onNewUserMsg(new UserMsg(authorId, rowId, createdDate));
                }
            }
            if (skipIt) {
                Log.w(TAG, "insertMsgBySender, the message was skipped: " + msg.toString(2));
            }
        } catch (JSONException e) {
            Log.e(TAG, "insertMsgBySender: " + e.toString());
            e.printStackTrace();
            if (msg != null) {
                Log.w(TAG, "msg: " + msg.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "insertMsgBySender: " + e.toString());
            e.printStackTrace();
        }

        return rowId;
    }

    /**
     * Insert a User row from a JSONObject. The same as {@link #insertUserFromJSONObject(JSONObject, LatestUserMessages)} 
     * but it saves latest user message internally
     */
    public long insertUserFromJSONObject(JSONObject user) throws JSONException, SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long userId = insertUserFromJSONObject(user, lum);
        lum.save();
        return userId;
    }
    
    /**
     * Insert a User row from a JSONObject.
     * 
     * @param user - The row to insert
     * @param lum The method should add to this object information on all messages that were added or updated.
     * @return id of the User added/updated. 0 in case of an error
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertUserFromJSONObject(JSONObject user, LatestUserMessages lum) throws JSONException, SQLiteConstraintException {
        String userName = "";
        if (user.has("screen_name")) {
            userName = user.getString("screen_name");
            if (SharedPreferencesUtil.isEmpty(userName)) {
                userName = "";
            }
        }
        String rowOid = "";
        if (user.has("id_str")) {
            rowOid = user.getString("id_str");
        } else if (user.has("id")) {
            rowOid = user.getString("id");
        } 
        Long originId = 0L;
        Long rowId = 0L;
        try {
            originId = Long.parseLong(user.getString(MyDatabase.User.ORIGIN_ID));
        } 
        catch (Exception e) {}
        finally {
            if (originId == 0) {
                originId = ma.getOriginId();
            }
        }
        if (SharedPreferencesUtil.isEmpty(rowOid)) {
            rowOid = "";
        } else {
            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(OidEnum.USER_OID, originId, rowOid);
        }
        if (rowId == 0) {
            // Try to Lookup by Username
            if (SharedPreferencesUtil.isEmpty(userName)) {
                Log.w(TAG, "insertUserFromJSONObject - no username: " + user.toString(2));
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
            if (user.has("name")) {
                values.put(MyDatabase.User.REAL_NAME, user.getString("name"));
            }
            if (user.has("profile_image_url")) {
                values.put(MyDatabase.User.AVATAR_URL, user.getString("profile_image_url"));
            }
            if (user.has("description")) {
                values.put(MyDatabase.User.DESCRIPTION, user.getString("description"));
            }
            if (user.has("url")) {
                values.put(MyDatabase.User.HOMEPAGE, user.getString("url"));
            }
            
            if (user.has("created_at")) {
                Long created = 0L;
                String createdAt = user.getString("created_at");
                if (createdAt.length() > 0) {
                    created = Date.parse(createdAt);
                }
                if (created > 0) {
                    values.put(MyDatabase.User.CREATED_DATE, created);
                }
            }

            if (!user.isNull("following")) {
                boolean followed = false;
                try {
                    followed = user.getBoolean("following");
                    values.put(MyDatabase.FollowingUser.USER_FOLLOWED, followed);
                    MyLog.v(TAG, "insertUserFromJSONObject: '" + userName + "' is " + (followed ? "followed" : "not followed") );
                } catch (JSONException e) {
                    Log.e(TAG, "insertUserFromJSONObject error; following='" + user.getString("following") +"'. " + e.toString());
                }
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

            if (user.has("status")) {
                JSONObject latestMessage = user.getJSONObject("status");
                // This message doesn't have a sender!
                insertMsgBySender(latestMessage, lum, rowId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "insertUserFromJSONObject: " + e.toString());
        }
        return rowId;
    }
    
    
    /**
     * Insert one message from a JSONObject. Notifies listeners of the change. 
     * Saves latest User message information.
     * 
     * @param jo
     * @return id of the inserted message
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertMsgFromJSONObject(JSONObject jo) throws JSONException,
            SQLiteConstraintException {
        LatestUserMessages lum = new LatestUserMessages();
        long rowId = insertMsgFromJSONObject(jo, lum);
        lum.save();
        mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
        return rowId;
    }

    /**
     * Return the number of new messages, see {@link MyDatabase.Msg} .
     * 
     * @return integer
     */
    public int messagesCount() {
        return mMessages;
    }

    /**
     * Return the number of new Replies.
     * @return integer
     */
    public int repliesCount() {
        return mReplies;
    }

    /**
     * Return the number of new Mentions.
     * @return integer
     */
    public int mentionsCount() {
        return mMentions;
    }
}
