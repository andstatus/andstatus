/* 
 * Copyright (c) 2012-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;
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
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
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
 * Stores (updates) messages and users
 *  from a Social network into a database.
 * 
 * @author yvolk@yurivolkov.com
 */
public class DataUpdater {
    private static final String TAG = DataUpdater.class.getSimpleName();
    static final String MSG_ASSERTION_KEY = "insertOrUpdateMsg";
    private final CommandExecutionContext execContext;
    private LatestUserMessages lum = new LatestUserMessages();
    private KeywordsFilter keywordsFilter = new KeywordsFilter(
            SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));

    public DataUpdater(MyAccount ma) {
        this(new CommandExecutionContext(CommandData.newAccountCommand(CommandEnum.EMPTY, ma)));
    }
    
    public DataUpdater(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }

    public long onActivity(MbActivity mbActivity) {
        return onActivity(mbActivity, true);
    }

    /**
     * TODO: parse Actor, ActivityType etc...
     * @return id of the "MbObject"
     */
    public long onActivity(MbActivity activity, boolean saveLum) {
        if (activity == null || activity.isEmpty()) {
            return 0;
        }
        long id = 0;
        updateUser(activity.getActor().update(activity.accountUser));
        switch (activity.getObjectType()) {
            case ACTIVITY:
                return onActivity(activity.getActivity(), saveLum);
            case MESSAGE:
                id = updateMessage(activity, true);
                break;
            case USER:
                id = updateUser(activity);
                break;
            default:
                if (activity.isEmpty()) {
                    MyLog.w(TAG, "activity is empty, skipping: " + activity);
                    return 0;
                }
                break;
        }
        if (saveLum) {
            saveLum();
        }
        return id;
    }

    public void saveLum() {
        lum.save();
    }

    private long updateMessage(@NonNull MbActivity activity, boolean updateUsers) {
        final String funcName = "updateMessage";
        final MbMessage message = activity.getMessage();
        try {
            ContentValues values = new ContentValues();
            MyAccount me = execContext.getMyContext().persistentAccounts().
                    fromOriginAndOid(message.originId, activity.accountUser.oid);
            if (!me.isValid()) {
                MyLog.w(TAG, funcName +"; my account is invalid, skipping: " + message.toString());
                return 0;
            }

            if (updateUsers) {
                if (activity.isAuthorActor()) {
                    message.setAuthor(activity.getActor());
                } else {
                    updateUser(message.getAuthor().update(activity.accountUser, activity.getActor()));
                }
            }

            if (message.isReblogged() && activity.getActor().userId != 0) {
                if (!activity.isActorMe()) {
                    values.put(MsgOfUserTable.USER_ID + MsgOfUserTable.SUFFIX_FOR_OTHER_USER, activity.getActor().userId);
                }
                values.put(MsgOfUserTable.REBLOGGED +
                        (activity.isActorMe() ? "" : MsgOfUserTable.SUFFIX_FOR_OTHER_USER), 1);
                // Remember original id of the reblog message
                // We will need it to "undo reblog" for our reblog
                values.put(MsgOfUserTable.REBLOG_OID +
                        (activity.isActorMe() ? "" : MsgOfUserTable.SUFFIX_FOR_OTHER_USER), message.getReblogOid());
            }

            if (message.getAuthor().userId != 0) {
                values.put(MsgTable.AUTHOR_ID, message.getAuthor().userId);
            }

            if (message.msgId == 0) {
                message.msgId = MyQuery.oidToId(OidEnum.MSG_OID, message.originId, message.oid);
            }

            /*
             * Is the row first time retrieved from a Social Network?
             * Message can already exist in this these cases:
             * 1. There was only "a stub" stored (without a sent date and a body)
             * 2. Message was "unsent"
             */
            boolean isFirstTimeLoaded = message.getStatus() == DownloadStatus.LOADED || message.msgId == 0;
            boolean isDraftUpdated = !isFirstTimeLoaded
                    && (message.getStatus() == DownloadStatus.SENDING || message.getStatus() == DownloadStatus.DRAFT);

            long updatedDateStored = 0;
            long sentDateStored = 0;
            if (message.msgId != 0) {
                DownloadStatus statusStored = DownloadStatus.load(
                        MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId));
                sentDateStored = MyQuery.msgIdToLongColumnValue(MsgTable.SENT_DATE, message.msgId);
                updatedDateStored = MyQuery.msgIdToLongColumnValue(MsgTable.UPDATED_DATE, message.msgId);
                if (isFirstTimeLoaded) {
                    isFirstTimeLoaded = statusStored != DownloadStatus.LOADED;
                }
            }

            boolean isNewerThanInDatabase = message.getUpdatedDate() > updatedDateStored;
            if (isFirstTimeLoaded || isDraftUpdated || isNewerThanInDatabase) {
                values.put(MsgTable.MSG_STATUS, message.getStatus().save());
                values.put(MsgTable.UPDATED_DATE, message.getUpdatedDate());

                if (activity.getActor().userId != 0) {
                    // Store the Sender only for the first retrieved message.
                    // Don't overwrite the original sender (especially the first reblogger) 
                    values.put(MsgTable.ACTOR_ID, activity.getActor().userId);
                }
                if (!TextUtils.isEmpty(message.oid)) {
                    values.put(MsgTable.MSG_OID, message.oid);
                }
                values.put(MsgTable.ORIGIN_ID, message.originId);
                if (!TextUtils.isEmpty(message.conversationOid)) {
                    values.put(MsgTable.CONVERSATION_OID, message.conversationOid);
                }
                values.put(MsgTable.BODY, message.getBody());
                values.put(MsgTable.BODY_TO_SEARCH, message.getBodyToSearch());
            }

            if (message.sentDate > sentDateStored) {
                // Remember the latest sent date in order to see the reblogged message 
                // at the top of the sorted list 
                values.put(MsgTable.SENT_DATE, message.sentDate);
            }

            boolean isDirectMessage = false;
            if (message.recipient != null) {
                long recipientId = updateUser(message.recipient.update(activity.accountUser, activity.getActor()));
                values.put(MsgTable.RECIPIENT_ID, recipientId);
                if (recipientId == me.getUserId() || activity.isAuthorMe()) {
                    isDirectMessage = true;
                    values.put(MsgOfUserTable.DIRECTED, 1);
                    MyLog.v(this, "Message '" + message.oid + "' is Direct for " + me.getAccountName() );
                }
            }
            if (!message.isSubscribedByMe().equals(TriState.FALSE) && message.getUpdatedDate() > 0) {
                if (execContext.getTimeline().getTimelineType() == TimelineType.HOME
                        || (!isDirectMessage && activity.isAuthorMe())) {
                    message.setSubscribedByMe(TriState.TRUE);
                }
            }
            if (message.isSubscribedByMe().equals(TriState.TRUE)) {
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

            if (message.getFavoritedByMe() != TriState.UNKNOWN) {
                values.put(MsgOfUserTable.FAVORITED, message.getFavoritedByMe().toBoolean(false));
                MyLog.v(this, "Message '" + message.oid + "' "
                        + (message.getFavoritedByMe().toBoolean(false) ? "favorited" : "unfavorited")
                        + " by " + me.getAccountName());
            }

            boolean mentioned = isMentionedAndPutInReplyToMessage(activity, me, values);

            if (message.lookupConversationId() != 0) {
                values.put(MsgTable.CONVERSATION_ID, message.getConversationId());
            }

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, ((message.msgId==0) ? "insertMsg" : "updateMsg " + message.msgId)
                        + ":" + message.getStatus()
                        + (isFirstTimeLoaded ? " new;" : "")
                        + (isDraftUpdated ? " draft updated;" : "")
                        + (isNewerThanInDatabase ? " newer, updated at " + new Date(message.getUpdatedDate()) + ";"
                        : "") );
            }
            
            if (MyContextHolder.get().isTestRun()) {
                MyContextHolder.get().put(new AssertionData(MSG_ASSERTION_KEY, values));
            }
            if (message.msgId == 0) {
                Uri msgUri = execContext.getContext().getContentResolver().insert(
                        MatchedUri.getMsgUri(me.getUserId(), 0), values);
                message.msgId = ParsedUri.fromUri(msgUri).getMessageId();

                if (message.getConversationId() == 0) {
                    ContentValues values2 = new ContentValues();
                    values2.put(MsgTable.CONVERSATION_ID, message.setConversationIdFromMsgId());
                    execContext.getContext().getContentResolver().update(msgUri, values2, null, null);
                }
            } else {
                Uri msgUri = MatchedUri.getMsgUri(me.getUserId(), message.msgId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
            }

            if (isFirstTimeLoaded || isDraftUpdated) {
                saveAttachments(message);
            }

            if (!keywordsFilter.matchedAny(message.getBodyToSearch())) {
                if (message.getUpdatedDate() > 0) {
                    execContext.getResult().incrementDownloadedCount();
                }
                if (isNewerThanInDatabase) {
                    execContext.getResult().incrementMessagesCount();
                    if (mentioned) {
                        execContext.getResult().incrementMentionsCount();
                    }
                    if (isDirectMessage) {
                        execContext.getResult().incrementDirectCount();
                    }
                }
            }
            // Remember all messages that we added or updated
            lum.onNewUserMsg(new UserMsg(activity.getActor().userId, message.msgId, message.sentDate));
            if ( !activity.isAuthorActor()) {
                lum.onNewUserMsg(new UserMsg(message.getAuthor().userId, message.msgId, message.getUpdatedDate()));
            }

            for (MbMessage reply : message.replies) {
                DataUpdater di = new DataUpdater(execContext);
                di.updateMessage(reply.update(activity.accountUser), true);
            }
        } catch (Exception e) {
            MyLog.e(this, funcName, e);
        }
        return message.msgId;
    }

    private boolean isMentionedAndPutInReplyToMessage(MbActivity activity, MyAccount me, ContentValues values) {
        MbMessage message = activity.getMessage();
        boolean mentioned = execContext.getTimeline().getTimelineType() == TimelineType.MENTIONS;
        Long inReplyToUserId = 0L;
        final MbMessage inReplyToMessage = message.getInReplyTo();
        if (inReplyToMessage.nonEmpty()) {
            if (TextUtils.isEmpty(inReplyToMessage.conversationOid)) {
                inReplyToMessage.setConversationOid(message.conversationOid);
            }
            inReplyToMessage.setSubscribedByMe(TriState.FALSE);
            // Type of the timeline is ALL meaning that message does not belong to this timeline
            DataUpdater di = new DataUpdater(execContext);
            // If the Msg is a Reply to another message
            Long inReplyToMessageId = di.updateMessage(inReplyToMessage.update(activity.accountUser), true);
            if (inReplyToMessage.getAuthor().nonEmpty()) {
                inReplyToUserId = MyQuery.oidToId(OidEnum.USER_OID, message.originId, inReplyToMessage.getAuthor().oid);
            } else if (inReplyToMessageId != 0) {
                inReplyToUserId = MyQuery.msgIdToLongColumnValue(MsgTable.ACTOR_ID, inReplyToMessageId);
            }
            if (inReplyToMessageId != 0) {
                values.put(MsgTable.IN_REPLY_TO_MSG_ID, inReplyToMessageId);
            }
        } else {
            inReplyToUserId = getReplyToUserIdInBody(activity);
        }
        if (inReplyToUserId != 0) {
            values.put(MsgTable.IN_REPLY_TO_USER_ID, inReplyToUserId);

            if (me.getUserId() == inReplyToUserId) {
                values.put(MsgOfUserTable.REPLIED, 1);
                // We count replies as Mentions
                mentioned = true;
            }
        }

        // Check if current user was mentioned in the text of the message
        if (message.getBody().length() > 0 
                && !mentioned 
                && message.getBody().contains("@" + me.getUsername())) {
            mentioned = true;
        }
        if (mentioned) {
            values.put(MsgOfUserTable.MENTIONED, 1);
        }
        return mentioned;
    }

    private long getReplyToUserIdInBody(MbActivity activity) {
        long userId = 0;
        List<MbUser> users = activity.getMessage().getAuthor().extractUsersFromBodyText(
                activity.getMessage().getBody(), true);
        if (users.size() > 0) {
            userId = users.get(0).userId;
            if (userId == 0) {
                userId = updateUser(users.get(0).update(activity.accountUser, activity.getActor()));
            }
        }
        return userId;
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

    /**
     * @return userId
     */
    private long updateUser(MbActivity activity) {
        MbUser mbUser = activity.getUser();
        final String method = "updateUser";
        if (mbUser.isEmpty()) {
            MyLog.v(this, method + "; mbUser is empty");
            return 0;
        }
        
        long userId = mbUser.lookupUserId();
        if (userId != 0 && mbUser.isPartiallyDefined() && mbUser.followedByActor.equals(TriState.UNKNOWN)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + "; Skipping partially defined: " + mbUser.toString());
            }
            return userId;
        }

        long originId = mbUser.originId;
        String userOid = (userId == 0 && !mbUser.isOidReal()) ? mbUser.getTempOid() : mbUser.oid;
        try {
            ContentValues values = new ContentValues();
            if (userId == 0 || !mbUser.isPartiallyDefined()) {
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
            }

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

            MyAccount myActor = execContext.getMyContext().persistentAccounts().fromUserId(activity.getActor().userId);
            if (!myActor.isValid()) {
                myActor = execContext.getMyAccount();
            }
            if (mbUser.followedByActor != TriState.UNKNOWN
                    && activity.getActor().userId == myActor.getUserId()) {
                values.put(FriendshipTable.FOLLOWED,
                        mbUser.followedByActor.toBoolean(false));
                MyLog.v(this,
                        "User '" + mbUser.getUserName() + "' is "
                                + (mbUser.followedByActor.toBoolean(false) ? "" : "not ")
                                + "followed by " + myActor.getAccountName());
            }
            
            // Construct the Uri to the User
            Uri userUri = MatchedUri.getUserUri(myActor.getUserId(), userId);
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
                updateMessage(mbUser.getLatestMessage().update(activity.accountUser), false);
            }
        } catch (Exception e) {
            MyLog.e(this, "insertUser exception", e);
        }
        MyLog.v(this, "insertUser, userId=" + userId + "; oid=" + userOid);
        return userId;
    }

    public void downloadOneMessageBy(String userOid) throws ConnectionException {
        List<MbActivity> activities = execContext.getConnection().getTimeline(
                TimelineType.USER.getConnectionApiRoutine(), TimelinePosition.EMPTY,
                TimelinePosition.EMPTY, 1, userOid);
        for (MbActivity item : activities) {
            onActivity(item, false);
        }
        saveLum();
    }

}
