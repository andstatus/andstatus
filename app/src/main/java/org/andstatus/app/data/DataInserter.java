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
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbTimelineItem;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Stores ("inserts" - adds or updates) messages and users
 *  from a Social network into a database.
 * 
 * @author yvolk@yurivolkov.com
 */
public class DataInserter {
    private static final String TAG = DataInserter.class.getSimpleName();
    static final String MSG_ASSERTION_KEY = "insertOrUpdateMsg";
    private final CommandExecutionContext execContext;
    private KeywordsFilter keywordsFilter = new KeywordsFilter(
            SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));

    public DataInserter(MyAccount ma) {
        this(new CommandExecutionContext(CommandData.newAccountCommand(CommandEnum.EMPTY, ma)));
    }
    
    public DataInserter(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }
    
    public long insertOrUpdateMsg(MbMessage message, LatestUserMessages lum) {
        return insertOrUpdateMsgInner(message, lum, true);
    }
    
    private long insertOrUpdateMsgInner(MbMessage messageIn, LatestUserMessages lum, boolean updateSender) {
        final String funcName = "Inserting/updating msg";
        MbMessage message = messageIn;
        if (message.isEmpty()) {
            MyLog.w(TAG, funcName +"; the message is empty, skipping: " + message.toString());
            return 0;
        }
        try {
            ContentValues values = new ContentValues();

            if (message.sentDate > 0) {
                if (!keywordsFilter.matched(message.getBody())) {
                    execContext.getResult().incrementDownloadedCount();
                }
            }

            if (message.actor == null) {
                message.actor = execContext.getMyAccount().toMbUser();
            } else {
                insertOrUpdateUser(message.actor, lum);
            }

            if (updateSender) {
                insertOrUpdateUser(message.sender, lum);
            }

            // Is this a reblog?
            if (message.rebloggedMessage != null) {
                if (message.rebloggedMessage.sender != null) {
                    insertOrUpdateUser(message.rebloggedMessage.sender, lum);
                }
                if (message.getSenderId() != 0) {
                    if (message.getSenderId() != execContext.getMyAccount().getUserId()) {
                        values.put(MsgOfUserTable.USER_ID
                                + MsgOfUserTable.SUFFIX_FOR_OTHER_USER, message.getSenderId());
                    }
                    values.put(MsgOfUserTable.REBLOGGED
                            + (message.getSenderId() == execContext.getMyAccount().getUserId() ? ""
                            : MsgOfUserTable.SUFFIX_FOR_OTHER_USER), 1);
                    // Remember original id of the reblog message
                    // We will need it to "undo reblog" for our reblog
                    if (!SharedPreferencesUtil.isEmpty(message.oid)) {
                        values.put(MsgOfUserTable.REBLOG_OID
                                + (message.getSenderId() == execContext.getMyAccount().getUserId() ? ""
                                : MsgOfUserTable.SUFFIX_FOR_OTHER_USER), message.oid);
                    }
                }

                // And replace reblog with original message!
                // So we won't have lots of reblogs but rather one original message
                message = message.rebloggedMessage;
                if (messageIn.msgId != 0) {
                    message.msgId = messageIn.msgId;
                }
                if (messageIn.conversationId != 0) {
                    message.conversationId = messageIn.conversationId;
                }
                message.setConversationOid(messageIn.conversationOid);
                if (message.sentDate == 0) {
                    message.sentDate = messageIn.sentDate;
                }
            }

            long authorId = message.getSenderId();
            if (authorId == 0) {
                authorId = messageIn.getSenderId();
            }
            if (authorId != 0) {
                values.put(MsgTable.AUTHOR_ID, authorId);
            }

            if (message.msgId == 0) {
                // Lookup the System's (AndStatus) id from the Originated system's id
                message.msgId = MyQuery.oidToId(OidEnum.MSG_OID, execContext.getMyAccount().getOriginId(), message.oid);
            }

            /**
             * Is the row first time retrieved from a Social Network?
             * Message can already exist in this these cases:
             * 1. There was only "a stub" stored (without a sent date and a body)
             * 2. Message was "unsent"
             */
            boolean isFirstTimeLoaded = message.getStatus() == DownloadStatus.LOADED || message.msgId == 0;
            boolean isDraftUpdated = !isFirstTimeLoaded
                    && (message.getStatus() == DownloadStatus.SENDING
                        || message.getStatus() == DownloadStatus.DRAFT);

            long sentDateStored = 0;
            if (message.msgId != 0) {
                DownloadStatus statusStored = DownloadStatus.load(
                        MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId));
                sentDateStored = MyQuery.msgIdToLongColumnValue(MsgTable.SENT_DATE, message.msgId);
                if (isFirstTimeLoaded) {
                    isFirstTimeLoaded = statusStored != DownloadStatus.LOADED;
                }
            }

            if (isFirstTimeLoaded || isDraftUpdated) {
                values.put(MsgTable.MSG_STATUS, message.getStatus().save());
                values.put(MsgTable.CREATED_DATE, message.sentDate);
                
                if (messageIn.getSenderId() != 0) {
                    // Store the Sender only for the first retrieved message.
                    // Don't overwrite the original sender (especially the first reblogger) 
                    values.put(MsgTable.SENDER_ID, messageIn.getSenderId());
                }
                if (!TextUtils.isEmpty(message.oid)) {
                    values.put(MsgTable.MSG_OID, message.oid);
                }
                values.put(MsgTable.ORIGIN_ID, message.originId);
                if (!TextUtils.isEmpty(message.conversationOid)) {
                    values.put(MsgTable.CONVERSATION_OID, message.conversationOid);
                }
                values.put(MsgTable.BODY, message.getBody());
            }
            
            /**
             * Is the message newer than stored in the database (e.g. the newer reblog of existing message)
             */
            boolean isNewerThanInDatabase = message.sentDate > sentDateStored;
            if (isNewerThanInDatabase) {
                // Remember the latest sent date in order to see the reblogged message 
                // at the top of the sorted list 
                values.put(MsgTable.SENT_DATE, message.sentDate);
            }

            boolean isDirectMessage = false;
            if (message.recipient != null) {
                long recipientId = insertOrUpdateUser(message.recipient, lum);
                values.put(MsgTable.RECIPIENT_ID, recipientId);
                if (recipientId == execContext.getMyAccount().getUserId() ||
                        messageIn.getSenderId() == execContext.getMyAccount().getUserId()) {
                    isDirectMessage = true;
                    values.put(MsgOfUserTable.DIRECTED, 1);
                    MyLog.v(this, "Message '" + message.oid + "' is Directed to " 
                            + execContext.getMyAccount().getAccountName() );
                }
            }
            if (!message.isSubscribed().equals(TriState.FALSE)) {
                if (execContext.getTimeline().getTimelineType() == TimelineType.HOME
                        || (!isDirectMessage && messageIn.getSenderId() == execContext.getMyAccount().getUserId())) {
                    message.setSubscribed(TriState.TRUE);
                }
            }
            if (message.isSubscribed().equals(TriState.TRUE)) {
                values.put(MsgOfUserTable.SUBSCRIBED, 1);
            }
            if (!TextUtils.isEmpty(message.via)) {
                values.put(MsgTable.VIA, message.via);
            }
            if (!TextUtils.isEmpty(message.url)) {
                values.put(MsgTable.URL, message.url);
            }
            if (message.isPublic()) {
                values.put(MsgTable.PUBLIC, 1);
            }

            if (message.favoritedByActor != TriState.UNKNOWN
                    && message.actor.userId == execContext.getMyAccount().getUserId()) {
                values.put(MsgOfUserTable.FAVORITED,
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

            putConversationId(message, values);
            
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, ((message.msgId==0) ? "insertMsg" : "updateMsg " + message.msgId)
                        + ":" + message.getStatus()
                        + (isFirstTimeLoaded ? " new;" : "")
                        + (isDraftUpdated ? " draft updated;" : "")
                        + (isNewerThanInDatabase ? " newer, sent at " + new Date(message.sentDate).toString() + ";" : "") );
            }
            
            if (MyContextHolder.get().isTestRun()) {
                MyContextHolder.get().put(new AssertionData(MSG_ASSERTION_KEY, values));
            }
            if (message.msgId == 0) {
                Uri msgUri = execContext.getContext().getContentResolver().insert(
                        MatchedUri.getMsgUri(execContext.getMyAccount().getUserId(), 0), values);
                message.msgId = ParsedUri.fromUri(msgUri).getMessageId();
            } else {
                Uri msgUri = MatchedUri.getMsgUri(execContext.getMyAccount().getUserId(), message.msgId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
            }
            if (message.conversationId == 0) {
                message.conversationId = message.msgId;
                Uri msgUri = MatchedUri.getMsgUri(execContext.getMyAccount().getUserId(), message.msgId);
                ContentValues values2 = new ContentValues();
                values2.put(MsgTable.CONVERSATION_ID, message.conversationId);
                execContext.getContext().getContentResolver().update(msgUri, values2, null, null);
            }

            if (isFirstTimeLoaded || isDraftUpdated) {
                saveAttachments(message);
            }

            if (isNewerThanInDatabase && !keywordsFilter.matched(message.getBody())) {
                // This message is newer than already stored in our database, so count it!
                execContext.getResult().incrementMessagesCount();
                if (mentioned) {
                    execContext.getResult().incrementMentionsCount();
                }
                if (isDirectMessage) {
                    execContext.getResult().incrementDirectCount();
                }
            }
            if (messageIn.getSenderId() != 0) {
                // Remember all messages that we added or updated
                lum.onNewUserMsg(new UserMsg(messageIn.getSenderId(), message.msgId, message.sentDate));
            }
            if ( authorId != 0 && authorId != messageIn.getSenderId() ) {
                lum.onNewUserMsg(new UserMsg(authorId, message.msgId, message.sentDate));
            }

            for (MbMessage reply : message.replies) {
                DataInserter di = new DataInserter(execContext);
                di.insertOrUpdateMsg(reply, lum);
            }
        } catch (Exception e) {
            MyLog.e(this, funcName, e);
        }

        messageIn.msgId = message.msgId;
        messageIn.conversationId = message.conversationId;
        return messageIn.msgId;
    }

    private boolean isMentionedAndPutInReplyToMessage(MbMessage message, LatestUserMessages lum,
                                                      ContentValues values) {
        boolean mentioned = execContext.getTimeline().getTimelineType() == TimelineType.MENTIONS;
        Long inReplyToUserId = 0L;
        if (message.inReplyToMessage != null) {
            // Type of the timeline is ALL meaning that message does not belong to this timeline
            DataInserter di = new DataInserter(execContext);
            // If the Msg is a Reply to another message
            Long inReplyToMessageId = di.insertOrUpdateMsg(message.inReplyToMessage, lum);
            if (message.inReplyToMessage.sender != null) {
                inReplyToUserId = MyQuery.oidToId(OidEnum.USER_OID, message.originId, message.inReplyToMessage.sender.oid);
            } else if (inReplyToMessageId != 0) {
                inReplyToUserId = MyQuery.msgIdToLongColumnValue(MsgTable.SENDER_ID, inReplyToMessageId);
            }
            if (inReplyToMessageId != 0) {
                values.put(MsgTable.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
            }
        } else {
            inReplyToUserId = getReplyToUserIdInBody(message);
        }
        if (inReplyToUserId != 0) {
            values.put(MsgTable.IN_REPLY_TO_USER_ID, inReplyToUserId);

            if (execContext.getMyAccount().getUserId() == inReplyToUserId) {
                values.put(MsgOfUserTable.REPLIED, 1);
                // We count replies as Mentions
                mentioned = true;
            }
        }

        // Check if current user was mentioned in the text of the message
        if (message.getBody().length() > 0 
                && !mentioned 
                && message.getBody().contains("@" + execContext.getMyAccount().getUsername())) {
            mentioned = true;
        }
        if (mentioned) {
            values.put(MsgOfUserTable.MENTIONED, 1);
        }
        return mentioned;
    }

    private long getReplyToUserIdInBody(MbMessage message) {
        long userId = 0;
        MbUser author = message.sender;
        if (author == null) {
            author = message.actor;
        }
        if (author == null) {
            author = MbUser.fromOriginAndUserOid(message.originId, "");
        }
        List<MbUser> users = author.fromBodyText(message.getBody(), true);
        if (users.size() > 0) {
            userId = users.get(0).userId;
            if (userId == 0) {
                userId = insertOrUpdateUser(users.get(0));
            }
        }
        return userId;
    }

    private void putConversationId(MbMessage message, ContentValues values) {
        if (message.conversationId == 0 && message.msgId != 0) {
            message.conversationId = MyQuery.msgIdToLongColumnValue(MsgTable.CONVERSATION_ID, message.msgId);
        }
        if (!TextUtils.isEmpty(message.conversationOid)) {
            values.put(MsgTable.CONVERSATION_OID, message.url);
            if (message.conversationId == 0) {
                message.conversationId = MyQuery.conditionToLongColumnValue(MsgTable.TABLE_NAME, MsgTable.CONVERSATION_ID,
                        MsgTable.CONVERSATION_OID + "='" + message.conversationOid + "'");
            }
        }
        if (message.conversationId == 0 && message.inReplyToMessage != null) {
            if (message.inReplyToMessage.msgId != 0) {
                message.conversationId = MyQuery.msgIdToLongColumnValue(MsgTable.CONVERSATION_ID, message.inReplyToMessage.msgId);
            }
        }
        if (message.conversationId == 0) {
            message.conversationId = message.msgId;
        }
        if (message.conversationId != 0) {
            values.put(MsgTable.CONVERSATION_ID, message.conversationId);
        }
    }

    private void saveAttachments(MbMessage message) {
        List<Long> downloadIds = new ArrayList<>();
        for (MbAttachment attachment : message.attachments) {
            DownloadData dd = DownloadData.getThisForMessage(message.msgId, attachment.contentType, attachment.getUri());
            dd.saveToDatabase();
            downloadIds.add(dd.getDownloadId());
            switch (dd.getStatus()) {
                case LOADED:
                case HARD_ERROR:
                    break;
                default:
                    if (UriUtils.isDownloadable(dd.getUri())) {
                        if (attachment.contentType == MyContentType.IMAGE && MyPreferences.getDownloadAndDisplayAttachedImages()) {
                            dd.requestDownload();
                        }
                    } else {
                        AttachmentDownloader.load(dd.getDownloadId(), execContext.getCommandData());
                    }
                    break;
            }
        }
        DownloadData.deleteOtherOfThisMsg(message.msgId, downloadIds);
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
        final String method = "insertOrUpdateUser";
        if (mbUser == null || mbUser.isEmpty()) {
            MyLog.v(this, method + "; mbUser is empty");
            return 0;
        }
        
        long userId = mbUser.lookupUserId();
        if (userId != 0 && mbUser.isPartiallyDefined()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + "; Skipping partially defined: " + mbUser.toString());
            }
            return userId;
        }

        long originId = mbUser.originId;
        String userOid = (userId == 0 && !mbUser.isOidReal()) ? mbUser.getTempOid() : mbUser.oid;
        try {
            ContentValues values = new ContentValues();
            if (userId == 0 || mbUser.isOidReal()) {
                values.put(UserTable.USER_OID, userOid);
            }

            // Substitute required empty values with some temporary for a new entry only!
            String userName = mbUser.getUserName();
            if (SharedPreferencesUtil.isEmpty(userName)) {
                userName = "id:" + userOid;
            }
            values.put(UserTable.USERNAME, userName);
            String webFingerId = mbUser.getWebFingerId();
            if (SharedPreferencesUtil.isEmpty(webFingerId)) {
                webFingerId = userName;
            }
            values.put(UserTable.WEBFINGER_ID, webFingerId);
            String realName = mbUser.getRealName();
            if (SharedPreferencesUtil.isEmpty(realName)) {
                realName = userName;
            }
            values.put(UserTable.REAL_NAME, realName);
            // Enf of required attributes

            if (!SharedPreferencesUtil.isEmpty(mbUser.avatarUrl)) {
                values.put(UserTable.AVATAR_URL, mbUser.avatarUrl);
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.getDescription())) {
                values.put(UserTable.DESCRIPTION, mbUser.getDescription());
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.getHomepage())) {
                values.put(UserTable.HOMEPAGE, mbUser.getHomepage());
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.getProfileUrl())) {
                values.put(UserTable.PROFILE_URL, mbUser.getProfileUrl());
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.bannerUrl)) {
                values.put(UserTable.BANNER_URL, mbUser.bannerUrl);
            }
            if (!SharedPreferencesUtil.isEmpty(mbUser.location)) {
                values.put(UserTable.LOCATION, mbUser.location);
            }
            if (mbUser.msgCount > 0) {
                values.put(UserTable.MSG_COUNT, mbUser.msgCount);
            }
            if (mbUser.favoritesCount > 0) {
                values.put(UserTable.FAVORITES_COUNT, mbUser.favoritesCount);
            }
            if (mbUser.followingCount > 0) {
                values.put(UserTable.FOLLOWING_COUNT, mbUser.followingCount);
            }
            if (mbUser.followersCount > 0) {
                values.put(UserTable.FOLLOWERS_COUNT, mbUser.followersCount);
            }
            if (mbUser.getCreatedDate() > 0) {
                values.put(UserTable.CREATED_DATE, mbUser.getCreatedDate());
            }
            if (mbUser.getUpdatedDate() > 0) {
                values.put(UserTable.UPDATED_DATE, mbUser.getUpdatedDate());
            }

            long readerId;
            if (mbUser.actor != null) {
                readerId = insertOrUpdateUser(mbUser.actor, lum);
            } else {
                readerId = execContext.getMyAccount().getUserId();
            }
            if (mbUser.followedByActor != TriState.UNKNOWN
                    && readerId == execContext.getMyAccount().getUserId()) {
                values.put(FriendshipTable.FOLLOWED,
                        mbUser.followedByActor.toBoolean(false));
                MyLog.v(this,
                        "User '" + mbUser.getUserName() + "' is "
                                + (mbUser.followedByActor.toBoolean(false) ? "" : "not ")
                                + "followed by " + execContext.getMyAccount().getAccountName());
            }
            
            // Construct the Uri to the User
            Uri userUri = MatchedUri.getUserUri(execContext.getMyAccount().getUserId(), userId);
            if (userId == 0) {
                // There was no such row so add new one
                values.put(UserTable.ORIGIN_ID, originId);
                userId = ParsedUri.fromUri(
                        execContext.getContext().getContentResolver().insert(userUri, values))
                        .getUserId();
            } else if (values.size() > 0) {
                execContext.getContext().getContentResolver().update(userUri, values, null, null);
            }
            mbUser.userId = userId;
            if (mbUser.hasLatestMessage()) {
                insertOrUpdateMsgInner(mbUser.getLatestMessage(), lum, false);
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
        return rowId;
    }

    public void downloadOneMessageBy(String userOid, LatestUserMessages lum) throws ConnectionException {
        List<MbTimelineItem> messages = execContext.getMyAccount().getConnection().getTimeline(
                TimelineType.USER.getConnectionApiRoutine(), TimelinePosition.getEmpty(), 1, userOid);
        for (MbTimelineItem item : messages) {
            if (item.getType() == MbTimelineItem.ItemType.MESSAGE) {
                insertOrUpdateMsg(item.mbMessage, lum);
                break;
            }
        }
    }

}
