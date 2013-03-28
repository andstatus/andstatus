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
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
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
     * Number of Mentions received (through {@link TimelineDownloader#insertMsgFromJSONObject(JSONObject)} 
     */
    private int mMentions;
    /**
     * Number of Replies received (through {@link TimelineDownloader#insertMsgFromJSONObject(JSONObject)} 
     */
    private int mReplies;

    private MyAccount ma;

    private TimelineTypeEnum mTimelineType;
    
    /**
     * The timeline parameter. Used in the {@link TimelineTypeEnum#USER} timeline.
     */
    private long mUserId = 0;
    
    public LastMsgInfo lastMsgInfo;

    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType) {
        this(ma_in, context, timelineType, 0);
    }
    
    public TimelineDownloader(MyAccount ma_in, Context context, TimelineTypeEnum timelineType, long userId) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        ma = ma_in;
        // mApi = ma.getApi();
        mTimelineType = timelineType;
        mUserId = userId;
        
        switch (mTimelineType) {
            case HOME:
            case MENTIONS:
            case DIRECT:
            case ALL:
                break;
            case USER:
                if (mUserId == 0) {
                    Log.e(TAG, "UserId is required for the Timeline type: " + mTimelineType.save());
                }
                break;
            default:
                Log.e(TAG, "Unknown Timeline type: " + mTimelineType);
        }
        
        lastMsgInfo = new LastMsgInfo();
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

        String userOid =  "";
        if (mUserId != 0) {
            userOid =  MyProvider.idToOid(OidEnum.USER_OID, mUserId, 0);
        }
        
        long lastMsgId = lastMsgInfo.getLastMsgId();
        long lastMsgDate = lastMsgInfo.getLastMsgDate();
        
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            String strLog = "Loading timeline " + mTimelineType.save() + "; account=" + ma.getUsername();
            if (mUserId != 0) {
                strLog += "; user=" + MyProvider.userIdToName(mUserId);
            }
            if (lastMsgDate > 0) {
                strLog += "; since=" + (new Date(lastMsgDate).toString())
                        + "; last time downloaded at " +  (new Date(lastMsgInfo.getTimelineDate()).toString());
            }
            MyLog.d(TAG, strLog);
        }
        
        int limit = 200;
        if ((ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) && MyPreferences.isDataAvailable()) {
            String lastOid = MyProvider.idToOid(OidEnum.MSG_OID, lastMsgId, 0);
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
                case USER:
                    jArr = ma.getConnection().getUserTimeline(userOid, lastOid, limit);
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
                        long idInserted = insertMsgFromJSONObject(msg);
                        // Check if this message is newer than any we got earlier
                        if (created > lastMsgDate && idInserted > lastMsgId) {
                            lastMsgDate = created;
                            lastMsgId = idInserted;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (mMessages > 0) {
                // Notify all timelines, 
                // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
                mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
            }
            lastMsgInfo.saveLastMsgInfo(lastMsgId, lastMsgDate);
        }
        return ok;
    }
    
    /**
     * Insert a Timeline row from a JSONObject
     * or update existing one.
     * 
     * @param msg - The row to insert
     * @return id of this Message in our system (row in the database)
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertMsgFromJSONObject(JSONObject msg) throws JSONException, SQLiteConstraintException {
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
            if (msg.has("created_at")) {
                Long created = 0L;
                String createdAt = msg.getString("created_at");
                if (createdAt.length() > 0) {
                    created = Date.parse(createdAt);
                }
                if (created > 0) {
                    sentDate = created;
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
                // Author of that message
                JSONObject author;
                author = rebloggedMessage.getJSONObject("user");
                authorId = insertUserFromJSONObject(author);

                if (ma.getUserId() == senderId) {
                    // Msg was reblogged by current User (he is the Sender)
                    values.put(MyDatabase.MsgOfUser.REBLOGGED, 1);

                    // Remember original id of the reblog message
                    // We will need it to "undo reblog" for our reblog
                    if (!MyPreferences.isEmpty(rowOid)) {
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
                
                // Created date may be different for reblogs:
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

            if (MyPreferences.isEmpty(rowOid)) {
                Log.w(TAG, "insertFromJSONObject - no message id");
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
                boolean isNewer = true;
                /**
                 * Count this message. 
                 */
                boolean countIt = false;

                // Lookup the System's (AndStatus) id from the Originated system's id
                rowId = MyProvider.oidToId(MyDatabase.Msg.CONTENT_URI, ma.getOriginId(), rowOid);
                // Construct the Uri to the Msg
                Uri msgUri = MyProvider.getTimelineMsgUri(ma.getUserId(), rowId, false);

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
                        if (!MyPreferences.isEmpty(inReplyToUserOid)) {
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
                            if (!MyPreferences.isEmpty(inReplyToMessageOid)) {
                                inReplyToMessageId = MyProvider.oidToId(MyDatabase.Msg.CONTENT_URI, ma.getOriginId(), inReplyToMessageOid);
                                if (inReplyToMessageId == 0) {
                                    // Construct Related "Msg" from available info
                                    // and add it recursively
                                    JSONObject inReplyToMessage = new JSONObject();
                                    inReplyToMessage.put("id_str", inReplyToMessageOid);
                                    inReplyToMessage.put(senderObjectName, inReplyToUser);
                                    // Type of the timeline is ALL meaning that message does not belong to this timeline
                                    TimelineDownloader td = new TimelineDownloader(ma, mContext, MyDatabase.TimelineTypeEnum.ALL);
                                    inReplyToMessageId = td.insertMsgFromJSONObject(inReplyToMessage);
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
                    msgUri = mContentResolver.insert(MyProvider.getTimelineUri(ma.getUserId(), MyDatabase.TimelineTypeEnum.HOME, false), values);
                    rowId = MyProvider.uriToMessageId(msgUri);
                } else {
                  mContentResolver.update(msgUri, values, null, null);
                }
            }
            if (skipIt) {
                Log.w(TAG, "insertFromJSONObject, the message was skipped: " + msg.toString(2));
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
            if (MyPreferences.isEmpty(userName)) {
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
        if (MyPreferences.isEmpty(rowOid)) {
            rowOid = "";
        } else {
            // Lookup the System's (AndStatus) id from the Originated system's id
            rowId = MyProvider.oidToId(MyDatabase.User.CONTENT_URI, originId, rowOid);
        }
        if (rowId == 0) {
            // Try to Lookup by Username
            if (MyPreferences.isEmpty(userName)) {
                Log.w(TAG, "insertUserFromJSONObject - no username: " + user.toString(2));
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
     * Insert one message from a JSONObject. Takes an optional parameter to notify
     * listeners of the change.
     * 
     * @param jo
     * @param notify
     * @return id of the inserted message
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public long insertMsgFromJSONObject(JSONObject jo, boolean notify) throws JSONException,
            SQLiteConstraintException {
        long rowId = insertMsgFromJSONObject(jo);
        if (notify) {
            mContentResolver.notifyChange(MyProvider.TIMELINE_URI, null);
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
    
    /**
     * Retrieve and save information about the last downloaded message from this timeline
     */
    public class LastMsgInfo {
        boolean mLastMsgInfoRetrieved = false;
        /**
         * 0 - none were downloaded
         */
        long mLastMsgId = 0;
        /**
         * {@link MyDatabase.Msg#SENT_DATE} of the {@link #mLastMsgId}
         */
        long mLastMsgDate = 0;
        /**
         * Last date when this timeline was successfully downloaded
         */
        long mTimelineDate = 0;
        
        public LastMsgInfo() {
            loadLastMsgInfo();
        }

        /**
         * Retrieve information about the last downloaded message from this timeline
         */
        private void loadLastMsgInfo() {
            if (!mLastMsgInfoRetrieved) {
                long userId = mUserId;
                if (mTimelineType != TimelineTypeEnum.USER) {
                    userId = ma.getUserId();
                }
                mTimelineDate = MyProvider.userIdToLongColumnValue(mTimelineType.columnNameDate(), userId);
                mLastMsgId = MyProvider.userIdToLongColumnValue(mTimelineType.columnNameMsgId(), userId);
                mLastMsgDate = MyProvider.msgSentDate(mLastMsgId);
                if (mLastMsgId > 0 && mLastMsgDate == 0) {
                    MyLog.d(TAG, "There is no message with " + MyDatabase.Msg._ID + "=" + mLastMsgId + " yet"); 
                    mLastMsgId = 0;
                }
                
            }
        }
        
        /**
         * @return Id of the last downloaded message from this timeline
         */
        public long getLastMsgId() {
            return mLastMsgId;
        }
        /**
         * @return Date of the last downloaded message 
         */
        public long getLastMsgDate() {
            return mLastMsgDate;
        }

        /**
         * @return Last date when this timeline was successfully downloaded
         */
        public long getTimelineDate() {
            return mTimelineDate;
        }

        public void saveLastMsgInfo(long lastMsgId, long lastMsgDate) {
            mLastMsgId = lastMsgId;
            mLastMsgDate = lastMsgDate;
            mTimelineDate = System.currentTimeMillis();
            long userId = mUserId;
            if (mTimelineType != TimelineTypeEnum.USER) {
                userId = ma.getUserId();
            }
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                MyLog.v(TAG, "Timeline " + mTimelineType.save() 
                        + " for the user=" + MyProvider.userIdToName(userId) 
                        + " downloaded at " + (new Date(getTimelineDate()).toString()));
            }

            try {
                ContentValues values = new ContentValues();
                values.put(mTimelineType.columnNameDate(), mTimelineDate );
                values.put(mTimelineType.columnNameMsgId(), mLastMsgId );
                Uri userUri = ContentUris.withAppendedId(MyDatabase.User.CONTENT_URI, userId);
                mContentResolver.update(userUri, values, null, null);
            } catch (Exception e) {
                Log.e(TAG, "saveLastMsgInfo: " + e.toString());
            }
        
        }
        
        /**
         * @return true if it's time to auto update this timeline
         */
        public boolean itsTimeToAutoUpdate() {
            boolean blnOut = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_AUTOMATIC_UPDATES, false);
            if (!blnOut) {
                // Automatic updates are disabled
                return false;
            }
            long intervalMs = Integer.parseInt(MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_FETCH_FREQUENCY, "180")) * MyService.MILLISECONDS;
            long passedMs = System.currentTimeMillis() - getTimelineDate(); 
            blnOut = (passedMs > intervalMs);
            
            if (blnOut && MyLog.isLoggable(TAG, Log.VERBOSE)) {
                long userId = mUserId;
                if (mTimelineType != TimelineTypeEnum.USER) {
                    userId = ma.getUserId();
                }
                MyLog.v(TAG, "It's time to auto update " + mTimelineType.save() 
                        + " for the user=" + MyProvider.userIdToName(userId)
                        + ". Minutes passed=" + passedMs/1000/60);
            }
            return blnOut;
        }
    }
}
