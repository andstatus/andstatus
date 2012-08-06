/* 
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import java.util.Date;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;


/**
 * The class automates several different processes 
 * (and this is why maybe it needs to be refactored...):
 * 1. Downloads ("loads") Home and Messages timelines 
 *  (i.e. Tweets and Messages) from the Internet 
 *  (e.g. from twitter.com server) into local JSON objects.
 * 2. Stores ("inserts" -  adds or updates) JSON-ed Tweets or Messages
 *  in the database.
 *  The Tweets/Messages come both from process "1" above and from other 
 *  processes ("update status", "favorite/unfavorite", ...).
 *  In also deletes Tweets/Messages from the database.
 * 3. Purges old Tweets/Messages according to the User preferences.
 * 
 * @author torgny.bjers
 */
public class TimelineDownloader {

    private static final String TAG = "TimelineDownloader";

    private ContentResolver mContentResolver;

    private Context mContext;

    /**
     * Counter. These may be "general" or Direct messages...
     */
    private int mMessages;

    /**
     * Number of Mentions received (through {@link TimelineDownloader#insertFromJSONObject(JSONObject)} 
     */
    private int mMentions;
    /**
     * Number of Replies received (through {@link TimelineDownloader#insertFromJSONObject(JSONObject)} 
     */
    private int mReplies;

    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    // private OriginApiEnum mApi;

    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        ma = ma_in;
        // mApi = ma.getApi();
        mTimelineType = timelineType;
        
        switch (mTimelineType) {
            case HOME:
            case MENTIONS:
            case DIRECT:
            case ALL:
                break;
            default:
                Log.e(TAG, "Unknown Timeline type: " + mTimelineType);
        }
    }
    
    /**
     * Load Timeline (Home / DirectMessages) from the Internet
     * and store them in the local database.
     * 
     * @throws ConnectionException
     */
    public boolean loadTimeline() throws ConnectionException {
        boolean ok = false;
        mMessages = 0;
        mMentions = 0;
        mReplies = 0;
        
        if (mTimelineType == TimelineTypeEnum.ALL) {
            Log.e(TAG, "Invalid TimelineType for loadTimeline: " + mTimelineType);
            return ok;
        }

        long lastMsgId = ma.getMyAccountPreferences().getLong(MyAccount.KEY_LAST_TIMELINE_ID + mTimelineType.save(), 0);
        long lastDate = MyProvider.msgSentDate(lastMsgId);
        if (lastDate == 0) {
            MyLog.d(TAG, "There is no message with " + MyDatabase.Msg._ID + "=" + lastMsgId + " yet"); 
            lastMsgId = 0;
        }
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + " for " + ma.getUsername();
            if (lastDate > 0) {
                strLog += " since " + new Date(lastDate).toGMTString();
            }
            MyLog.d(TAG, strLog);
        }
        
        int limit = 200;
        if ((ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) && MyPreferences.isDataAvailable()) {
            String lastOid = MyProvider.idToOid(MyDatabase.Msg.CONTENT_URI, lastMsgId);
            JSONArray jArr = null;
            switch (mTimelineType) {
                case HOME:
                    jArr = ma.getConnection().getHomeTimeline(lastOid, limit);
                    break;
                case MENTIONS:
                    jArr = ma.getConnection().getMentionsTimeline(lastOid, limit);
                    break;
                case DIRECT:
                    jArr = ma.getConnection().getDirectMessages(lastOid, limit);
                    break;
                default:
                    Log.e(TAG, "Got unhandled Timeline type: " + mTimelineType.save());
                    break;
            }
            if (jArr != null) {
                ok = true;
                try {
                    for (int index = 0; index < jArr.length(); index++) {
                        if (!MyPreferences.isInitialized()) {
                            ok = false;
                            break;
                        }
                        
                        JSONObject msg = jArr.getJSONObject(index);
                        long created = 0L;
                        // This value will be SENT_DATE in the database
                        if (msg.has("created_at")) {
                            String createdAt = msg.getString("created_at");
                            if (createdAt.length() > 0) {
                                created = Date.parse(createdAt);
                            }
                        }
                        long idInserted = insertFromJSONObject(msg);
                        // Check if this message is newer than any we got earlier
                        if (created > lastDate && idInserted > lastMsgId) {
                            lastDate = created;
                            lastMsgId = idInserted;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (mMessages > 0) {
                mContentResolver.notifyChange(MyProvider.getTimelineUri(ma.getUserId()), null);
            }
            ma.getMyAccountPreferences().edit().putLong(MyAccount.KEY_LAST_TIMELINE_ID + mTimelineType.save(),
                    lastMsgId).commit();
        }
        return ok;
    }

    /**
     * Insert a Timeline row from a JSONObject
     * or update existing one.
     * 
     * @param msg - The row to insert
     * @return id of this row in the database
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertFromJSONObject(JSONObject msg) throws JSONException, SQLiteConstraintException {
        Long rowId = 0L;
        /**
         * Count this message. 
         */
        boolean countIt = false; 
        try {
            ContentValues values = new ContentValues();

            // We use Created date from this message even for retweets in order to
            // get natural order of the tweets.
            // Otherwise retweeted message may appear as old
            if (msg.has("created_at")) {
                Long created = 0L;
                String createdAt = msg.getString("created_at");
                if (createdAt.length() > 0) {
                    created = Date.parse(createdAt);
                }
                if (created > 0) {
                    values.put(MyDatabase.Msg.SENT_DATE, created);
                    values.put(MyDatabase.Msg.CREATED_DATE, created);
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
            sender = msg.getJSONObject(senderObjectName);
            senderId = insertUserFromJSONObject(sender);
            values.put(MyDatabase.Msg.SENDER_ID, senderId);
            
            // Author
            long authorId = senderId;
            // Is this retweet
            if (msg.has("retweeted_status")) {
                JSONObject retweetedMessage = msg.getJSONObject("retweeted_status");
                // Author of that message
                JSONObject author;
                author = retweetedMessage.getJSONObject("user");
                authorId = insertUserFromJSONObject(author);

                if (ma.getUserId() == senderId) {
                    // Msg was retweeted by current User (he is Sender)
                    values.put(MyDatabase.MsgOfUser.RETWEETED, 1);
                }
                
                // And replace retweet with original message!
                // So we won't have lots of retweets but rather one original message
                msg = retweetedMessage;

                // Created date may be different for retweets:
                if (msg.has("created_at")) {
                    Long created = 0L;
                    String createdAt = msg.getString("created_at");
                    if (createdAt.length() > 0) {
                        created = Date.parse(createdAt);
                    }
                    if (created > 0) {
                        values.put(MyDatabase.Msg.CREATED_DATE, created);
                    }
                }
            }
            values.put(MyDatabase.Msg.AUTHOR_ID, authorId);

            String rowOid = "";
            if (msg.has("id_str")) {
                rowOid = msg.getString("id_str");
            } else if (msg.has("id")) {
                // This is for identi.ca
                rowOid = msg.getString("id");
            } 
            
            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(MyDatabase.Msg.CONTENT_URI, ma.getOriginId(), rowOid);
            // Construct the Uri to the Msg
            Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), rowId);
            
            String body = "";
            if (msg.has("text")) {
                body = Html.fromHtml(msg.getString("text")).toString();
            }
            values.put(MyDatabase.Msg.MSG_OID, rowOid);
            values.put(MyDatabase.Msg.ORIGIN_ID, ma.getOriginId());
            values.put(MsgOfUser.TIMELINE_TYPE, mTimelineType.save());
            values.put(MyDatabase.Msg.BODY, body);

            // As of 2012-07-07 we count only messages that were not in a database yet.
            // Don't count Messages without body - this may be related messages which were not retrieved yet.
            countIt = (rowId == 0) && (body.length() > 0);
            
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
                    if (msg.has("source")) {
                        values.put(MyDatabase.Msg.VIA, msg.getString("source"));
                    }
                    if (msg.has("favorited")) {
                        values.put(MyDatabase.MsgOfUser.FAVORITED, MyPreferences.isTrue(msg.getString("favorited")));
                    }

                    if (msg.has("in_reply_to_user_id_str")) {
                        inReplyToUserOid = msg.getString("in_reply_to_user_id_str");
                    } else if (msg.has("in_reply_to_user_id")) {
                        // This is for identi.ca
                        inReplyToUserOid = msg.getString("in_reply_to_user_id");
                    }
                    if (MyPreferences.isEmpty(inReplyToUserOid)) {
                        inReplyToUserOid = "";
                    }
                    if (inReplyToUserOid.length()>0) {
                        if (msg.has("in_reply_to_screen_name")) {
                            inReplyToUserName = msg.getString("in_reply_to_screen_name");
                        }
                        inReplyToUserId = MyProvider.oidToId(MyDatabase.User.CONTENT_URI, ma.getOriginId(), inReplyToUserOid);
                        
                        // Construct "User" from available info
                        JSONObject inReplyToUser = new JSONObject();
                        inReplyToUser.put("id_str", inReplyToUserOid);
                        inReplyToUser.put("screen_name", inReplyToUserName);
                        if (inReplyToUserId == 0) {
                            inReplyToUserId = insertUserFromJSONObject(inReplyToUser);
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
                        if (MyPreferences.isEmpty(inReplyToMessageOid)) {
                            inReplyToUserOid = "";
                        }
                        if (inReplyToMessageOid.length() > 0) {
                            inReplyToMessageId = MyProvider.oidToId(MyDatabase.Msg.CONTENT_URI, ma.getOriginId(), inReplyToMessageOid);
                            if (inReplyToMessageId == 0) {
                                // Construct Related "Msg" from available info
                                // and add it recursively
                                JSONObject inReplyToMessage = new JSONObject();
                                inReplyToMessage.put("id_str", inReplyToMessageOid);
                                inReplyToMessage.put(senderObjectName, inReplyToUser);
                                // Type of the timeline is ALL meaning that message does not belong to this timeline
                                TimelineDownloader td = new TimelineDownloader(ma, mContext, MyDatabase.TimelineTypeEnum.ALL);
                                inReplyToMessageId = td.insertFromJSONObject(inReplyToMessage);
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
                    recipientId = insertUserFromJSONObject(recipient);
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
            
            if (rowId == 0) {
                // There was no such row so add new one
                msgUri = mContentResolver.insert(MyProvider.getTimelineUri(ma.getUserId()), values);
                rowId = Long.parseLong(msgUri.getPathSegments().get(3));
            } else {
              mContentResolver.update(msgUri, values, null, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "insertFromJSONObject: " + e.toString());
        }

        return rowId;
    }

    /**
     * Insert a User row from a JSONObject.
     * 
     * @param jo - The row to insert
     * @return id the inserted record of 0 in a case of an error
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertUserFromJSONObject(JSONObject user) throws JSONException, SQLiteConstraintException {
        String userName = "";
        if (user.has("screen_name")) {
            userName = user.getString("screen_name");
            if (userName.compareTo("null") == 0) {
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
        if (rowOid.length() > 0) {
            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(MyDatabase.User.CONTENT_URI, originId, rowOid);
        }
        if (rowId == 0) {
            // Try to Lookup by Username
            if (userName.length() < 1) {
                Log.e(TAG, "insertUserFromJSONObject - no username");
                return 0;
            }
            rowId = MyProvider.userNameToId(originId, userName);
        }
        
        try {
            ContentValues values = new ContentValues();

            if (rowOid.length()>0) {
                values.put(MyDatabase.User.USER_OID, rowOid);
            }
            values.put(MyDatabase.User.ORIGIN_ID, originId);
            if (userName.length()>0) {
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

            // Construct the Uri to the User
            Uri userUri = ContentUris.withAppendedId(MyDatabase.User.CONTENT_URI, rowId);
            if (rowId == 0) {
                // There was no such row so add new one
                userUri = mContentResolver.insert(MyDatabase.User.CONTENT_URI, values);
                rowId = Long.parseLong(userUri.getPathSegments().get(1));
            } else {
              mContentResolver.update(userUri, values, null, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "insertUserFromJSONObject: " + e.toString());
        }
        return rowId;
    }
    
    
    /**
     * Insert a row from a JSONObject. Takes an optional parameter to notify
     * listeners of the change.
     * 
     * @param jo
     * @param notify
     * @return id of the inserted message
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertFromJSONObject(JSONObject jo, boolean notify) throws JSONException,
            SQLiteConstraintException {
        long rowId = insertFromJSONObject(jo);
        if (notify) {
            // Construct the Uri to the Msg
            Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), rowId);
            mContentResolver.notifyChange(msgUri, null);
        }
        return rowId;
    }

    /**
     * Remove old records to ensure that the database does not grow too large.
     * Maximum number of records is configured in "history_size" preference
     * 
     * @return Number of deleted records
     */
    public int pruneOldRecords() {
        int nDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = MyPreferences
                .getDefaultSharedPreferences();

        // Don't delete messages which are favorited by any user
        String sqlNotFavorited = "NOT EXISTS ("
                + "SELECT * FROM " + MyDatabase.MSGOFUSER_TABLE_NAME + " AS gnf WHERE "
                + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg._ID + "=gnf." + MyDatabase.MsgOfUser.MSG_ID
                + " AND gnf." + MyDatabase.MsgOfUser.FAVORITED + "=1" 
                + ")";
        
        int maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long sinceTimestamp = 0;

        int nTweets = 0;
        int nToDeleteSize = 0;
        int nDeletedSize = 0;
        int maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long sinceTimestampSize = 0;
        try {
            if (maxDays > 0) {
                sinceTimestamp = System.currentTimeMillis() - maxDays * (1000L * 60 * 60 * 24);
                SelectionAndArgs sa = new SelectionAndArgs();
                sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.INS_DATE + " <  ?", new String[] {
                    String.valueOf(sinceTimestamp)
                });
                sa.selection += " AND " + sqlNotFavorited;
                nDeletedTime = mContentResolver.delete(MyDatabase.Msg.CONTENT_URI, sa.selection, sa.selectionArgs);
            }

            if (maxSize > 0) {
                nDeletedSize = 0;
                Cursor cursor = mContentResolver.query(MyDatabase.Msg.CONTENT_COUNT_URI, null, null, null, null);
                if (cursor.moveToFirst()) {
                    // Count is in the first column
                    nTweets = cursor.getInt(0);
                    nToDeleteSize = nTweets - maxSize;
                }
                cursor.close();
                if (nToDeleteSize > 0) {
                    // Find INS_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(MyDatabase.Msg.CONTENT_URI, new String[] {
                            MyDatabase.Msg.INS_DATE
                    }, null, null, "sent ASC LIMIT 0," + nToDeleteSize);
                    if (cursor.moveToLast()) {
                        sinceTimestampSize = cursor.getLong(0);
                    }
                    cursor.close();
                    if (sinceTimestampSize > 0) {
                        SelectionAndArgs sa = new SelectionAndArgs();
                        sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.INS_DATE + " <=  ?", new String[] {
                            String.valueOf(sinceTimestampSize)
                        });
                        sa.selection += " AND " + sqlNotFavorited;
                        nDeletedSize = mContentResolver.delete(MyDatabase.Msg.CONTENT_URI, sa.selection,
                                sa.selectionArgs);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "pruneOldRecords failed");
            e.printStackTrace();
        }
        nDeleted = nDeletedTime + nDeletedSize;
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG,
                    "pruneOldRecords; History time=" + maxDays + " days; deleted " + nDeletedTime
                            + " , since " + sinceTimestamp + ", now=" + System.currentTimeMillis());
            Log.v(TAG, "pruneOldRecords; History size=" + maxSize + " tweets; deleted "
                    + nDeletedSize + " of " + nTweets + " tweets, since " + sinceTimestampSize);
        }

        return nDeleted;
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
    
    /**
     * Destroy the status specified by ID.
     * 
     * @param statusId
     * @return Number of deleted records
     */
    public int destroyStatus(long statusId) {
        // TODO: Maybe we should use Timeline Uri...
        return mContentResolver.delete(MyDatabase.Msg.CONTENT_URI, MyDatabase.Msg._ID + " = " + statusId,
                null);
    }
}
