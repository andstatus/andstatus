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
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
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
    static final String MSG_ASSERTION_KEY = "updateMessage";
    private final CommandExecutionContext execContext;
    private LatestUserMessages lum = new LatestUserMessages();
    private KeywordsFilter keywordsFilter = new KeywordsFilter(
            SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));

    public static void onActivities(CommandExecutionContext execContext, List<MbActivity> activities) {
        DataUpdater dataUpdater = new DataUpdater(execContext);
        for (MbActivity mbActivity : activities) {
            dataUpdater.onActivity(mbActivity);
        }
    }

    public DataUpdater(MyAccount ma) {
        this(new CommandExecutionContext(CommandData.newAccountCommand(CommandEnum.EMPTY, ma)));
    }
    
    public DataUpdater(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }

    public MbActivity onActivity(MbActivity mbActivity) {
        return onActivity(mbActivity, true);
    }

    public MbActivity onActivity(MbActivity activity, boolean saveLum) {
        if (activity == null || activity.isEmpty()) {
            return activity;
        }
        updateUser(activity.getActor().update(activity.accountUser));
        switch (activity.getObjectType()) {
            case ACTIVITY:
                onActivity(activity.getActivity(), false);
                break;
            case MESSAGE:
                updateMessage(activity, true);
                break;
            case USER:
                updateUser(activity);
                break;
            default:
                throw new IllegalArgumentException("Unexpected activity: " + activity);
        }
        updateActivity(activity);
        if (saveLum) {
            saveLum();
        }
        return activity;
    }

    private void updateActivity(MbActivity activity) {
        if (!activity.isSubscribedByMe().equals(TriState.FALSE)
            && activity.getUpdatedDate() > 0
            && (activity.isAuthorMe() || activity.isActorMe()
                || activity.getMessage().audience().hasMyAccount(execContext.getMyContext()))) {
            activity.setSubscribedByMe(TriState.TRUE);
        }
        activity.save(execContext.getMyContext());
    }

    public void saveLum() {
        lum.save();
    }

    private void updateMessage(@NonNull MbActivity activity, boolean updateUsers) {
        updateMessage1(activity, updateUsers);
        DataUpdater.onActivities(execContext, activity.getMessage().replies);
    }

    private void updateMessage1(@NonNull MbActivity activity, boolean updateUsers) {
        final String method = "updateMessage";
        final MbMessage message = activity.getMessage();
        try {
            MyAccount me = execContext.getMyContext().persistentAccounts().fromUser(activity.accountUser);
            if (!me.isValid()) {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
            if (updateUsers) {
                if (activity.isAuthorActor()) {
                    activity.setAuthor(activity.getActor());
                } else {
                    updateUser(activity.getAuthor().update(activity.accountUser, activity.getActor()));
                }
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
            if (message.msgId != 0) {
                DownloadStatus statusStored = DownloadStatus.load(
                        MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId));
                updatedDateStored = MyQuery.msgIdToLongColumnValue(MsgTable.UPDATED_DATE, message.msgId);
                if (isFirstTimeLoaded) {
                    isFirstTimeLoaded = statusStored != DownloadStatus.LOADED;
                }
            }

            boolean isNewerThanInDatabase = message.getUpdatedDate() > updatedDateStored;
            if (!isFirstTimeLoaded && !isDraftUpdated && !isNewerThanInDatabase) {
                MyLog.v("MbMessage", "Skipped as not younger " + message);
                return;
            }

            // TODO: move as toContentValues() into MbMessage
            ContentValues values = new ContentValues();
            values.put(MsgTable.MSG_STATUS, message.getStatus().save());
            values.put(MsgTable.UPDATED_DATE, message.getUpdatedDate());

            if (activity.getAuthor().userId != 0) {
                values.put(MsgTable.AUTHOR_ID, activity.getAuthor().userId);
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

            activity.getMessage().addRecipientsFromBodyText(activity.getActor());
            updateInReplyTo(activity, values);
            for ( MbUser mbUser : message.audience().getRecipients()) {
                updateUser(mbUser.update(activity.accountUser, activity.getActor()));
            }
            if (activity.getMessage().audience().hasMyAccount(execContext.getMyContext())) {
                values.put(MsgTable.MENTIONED, TriState.TRUE.id);
            }

            if (!TextUtils.isEmpty(message.via)) {
                values.put(MsgTable.VIA, message.via);
            }
            if (!TextUtils.isEmpty(message.url)) {
                values.put(MsgTable.URL, message.url);
            }
            if (message.getPrivate().known()) {
                values.put(MsgTable.PRIVATE, message.getPrivate().id);
            }

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
                MyLog.v("MbMessage", "Added " + message);
            } else {
                Uri msgUri = MatchedUri.getMsgUri(me.getUserId(), message.msgId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
                MyLog.v("MbMessage", "Updated " + message);
            }
            message.audience().save(execContext.getMyContext(), message.originId, message.msgId);

            if (isFirstTimeLoaded || isDraftUpdated) {
                saveAttachments(message);
            }

            if (!keywordsFilter.matchedAny(message.getBodyToSearch())) {
                if (message.getStatus() == DownloadStatus.LOADED) {
                    execContext.getResult().incrementDownloadedCount();
                    execContext.getResult().incrementMessagesCount();
                    if (activity.getMessage().audience().hasMyAccount(execContext.getMyContext())) {
                        execContext.getResult().incrementMentionsCount();
                    }
                    if (activity.getMessage().isPrivate()) {
                        execContext.getResult().incrementDirectCount();
                    }
                }
            }
            // Remember all messages that we added or updated
            lum.onNewUserMsg(new UserMsg(activity.getActor().userId, activity.getId(), activity.getUpdatedDate()));
            if ( !activity.isAuthorActor()) {
                lum.onNewUserMsg(new UserMsg(activity.getAuthor().userId, activity.getId(), activity.getUpdatedDate()));
            }
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    private void updateInReplyTo(MbActivity activity, ContentValues values) {
        final MbActivity inReply = activity.getMessage().getInReplyTo();
        if (StringUtils.nonEmpty(inReply.getMessage().oid)) {
            if (TextUtils.isEmpty(inReply.getMessage().conversationOid)) {
                inReply.getMessage().setConversationOid(activity.getMessage().conversationOid);
            }
            new DataUpdater(execContext).onActivity(inReply);
            if (inReply.getMessage().msgId != 0) {
                activity.getMessage().addRecipient(inReply.getAuthor());
                values.put(MsgTable.IN_REPLY_TO_MSG_ID, inReply.getMessage().msgId);
                if (inReply.getAuthor().userId != 0) {
                    values.put(MsgTable.IN_REPLY_TO_USER_ID, inReply.getAuthor().userId);
                }
            }
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

    private void updateUser(MbActivity activity) {
        MbUser mbUser = activity.getUser();
        final String method = "updateUser";
        if (mbUser.isEmpty()) {
            MyLog.v(this, method + "; mbUser is empty");
            return;
        }
        MyAccount me = execContext.getMyContext().persistentAccounts().fromUser(activity.accountUser);
        if (!me.isValid()) {
            if (activity.accountUser.equals(mbUser)) {
                MyLog.d(this, method +"; adding my account " + activity.accountUser);
            } else {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
        }

        TriState followedByMe = TriState.UNKNOWN;
        TriState followedByActor = activity.type.equals(MbActivityType.FOLLOW) ? TriState.TRUE :
                activity.type.equals(MbActivityType.UNDO_FOLLOW) ? TriState.FALSE : TriState.UNKNOWN;
        if (mbUser.followedByMe.known()) {
            followedByMe = mbUser.followedByMe;
        } else if (activity.getActor().userId == me.getUserId()) {
            followedByMe = followedByActor;
        }

        long userId = mbUser.lookupUserId();
        if (userId != 0 && mbUser.isPartiallyDefined() && followedByMe.unknown()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + "; Skipping partially defined: " + mbUser.toString());
            }
            return;
        }

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

            if (followedByMe.known()) {
                values.put(FriendshipTable.FOLLOWED, followedByMe.toBoolean(false));
                MyLog.v(this, "User '" + me.getAccountName() + "' "
                                + (followedByMe.toBoolean(false) ? "follows" : "stop following ")
                                + mbUser.getUserName());
            }
            
            // Construct the Uri to the User
            Uri userUri = MatchedUri.getUserUri(me.getUserId(), userId);
            if (userId == 0) {
                // There was no such row so add new one
                values.put(UserTable.ORIGIN_ID, mbUser.originId);
                userId = ParsedUri.fromUri(
                        execContext.getContext().getContentResolver().insert(userUri, values))
                        .getUserId();
            } else if (values.size() > 0) {
                execContext.getContext().getContentResolver().update(userUri, values, null, null);
            }
            mbUser.userId = userId;
            if (mbUser.hasLatestMessage()) {
                updateMessage(mbUser.getLatestActivity(), false);
            }
        } catch (Exception e) {
            MyLog.e(this, method + "; userId=" + userId + "; oid=" + userOid, e);
        }
        MyLog.v(this, method + "; userId=" + userId + "; oid=" + userOid);
        return;
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
