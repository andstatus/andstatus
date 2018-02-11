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
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.note.KeywordsFilter;
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

import static org.andstatus.app.util.UriUtils.nonEmptyOid;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * Stores (updates) notes and actors
 *  from a Social network into a database.
 * 
 * @author yvolk@yurivolkov.com
 */
public class DataUpdater {
    static final String MSG_ASSERTION_KEY = "updateNote";
    private final CommandExecutionContext execContext;
    private LatestActorActivities lum = new LatestActorActivities();
    private KeywordsFilter keywordsFilter = new KeywordsFilter(
            SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS, ""));

    public static void onActivities(CommandExecutionContext execContext, List<AActivity> activities) {
        DataUpdater dataUpdater = new DataUpdater(execContext);
        for (AActivity mbActivity : activities) {
            dataUpdater.onActivity(mbActivity);
        }
    }

    public DataUpdater(MyAccount ma) {
        this(new CommandExecutionContext(CommandData.newAccountCommand(CommandEnum.EMPTY, ma)));
    }
    
    public DataUpdater(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }

    public AActivity onActivity(AActivity mbActivity) {
        return onActivity(mbActivity, true);
    }

    public AActivity onActivity(AActivity activity, boolean saveLum) {
        if (activity == null || activity.isEmpty()) {
            return activity;
        }
        updateObjActor(activity.getActor().update(activity.accountActor));
        switch (activity.getObjectType()) {
            case ACTIVITY:
                onActivity(activity.getActivity(), false);
                break;
            case NOTE:
                updateNote(activity, true);
                break;
            case ACTOR:
                updateObjActor(activity);
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

    private void updateActivity(AActivity activity) {
        if (!activity.isSubscribedByMe().equals(TriState.FALSE)
            && activity.getUpdatedDate() > 0
            && (activity.isMyActorOrAuthor(execContext.myContext)
                || activity.getNote().audience().containsMe(execContext.myContext))) {
            activity.setSubscribedByMe(TriState.TRUE);
        }
        activity.save(execContext.getMyContext());
        lum.onNewActorActivity(new ActorActivity(activity.getActor().actorId, activity.getId(), activity.getUpdatedDate()));
        if ( !activity.isAuthorActor()) {
            lum.onNewActorActivity(new ActorActivity(activity.getAuthor().actorId, activity.getId(), activity.getUpdatedDate()));
        }
        execContext.getResult().onNotificationEvent(activity.getNotificationEventType());
    }

    public void saveLum() {
        lum.save();
    }

    private void updateNote(@NonNull AActivity activity, boolean updateActors) {
        updateNote1(activity, updateActors);
        DataUpdater.onActivities(execContext, activity.getNote().replies);
    }

    private void updateNote1(@NonNull AActivity activity, boolean updateActors) {
        final String method = "updateNote1";
        final Note note = activity.getNote();
        try {
            MyAccount me = execContext.getMyContext().accounts().fromActorOfSameOrigin(activity.accountActor);
            if (!me.isValid()) {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
            if (updateActors) {
                if (activity.isAuthorActor()) {
                    activity.setAuthor(activity.getActor());
                } else {
                    updateObjActor(activity.getAuthor().update(activity.accountActor, activity.getActor()));
                }
            }
            if (note.noteId == 0) {
                note.noteId = MyQuery.oidToId(OidEnum.NOTE_OID, note.origin.getId(), note.oid);
            }

            /*
             * Is the row first time retrieved from a Social Network?
             * Note can already exist in this these cases:
             * 1. There was only "a stub" stored (without a sent date and a body)
             * 2. Note was "unsent"
             */
            boolean isFirstTimeLoaded = note.getStatus() == DownloadStatus.LOADED || note.noteId == 0;
            boolean isDraftUpdated = !isFirstTimeLoaded
                    && (note.getStatus() == DownloadStatus.SENDING || note.getStatus() == DownloadStatus.DRAFT);

            long updatedDateStored = 0;
            if (note.noteId != 0) {
                DownloadStatus statusStored = DownloadStatus.load(
                        MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note.noteId));
                updatedDateStored = MyQuery.noteIdToLongColumnValue(NoteTable.UPDATED_DATE, note.noteId);
                if (isFirstTimeLoaded) {
                    isFirstTimeLoaded = statusStored != DownloadStatus.LOADED;
                }
            }

            boolean isNewerThanInDatabase = note.getUpdatedDate() > updatedDateStored;
            if (!isFirstTimeLoaded && !isDraftUpdated && !isNewerThanInDatabase) {
                MyLog.v("Note", "Skipped as not younger " + note);
                return;
            }

            // TODO: move as toContentValues() into Note
            ContentValues values = new ContentValues();
            values.put(NoteTable.NOTE_STATUS, note.getStatus().save());
            values.put(NoteTable.UPDATED_DATE, note.getUpdatedDate());

            if (activity.getAuthor().actorId != 0) {
                values.put(NoteTable.AUTHOR_ID, activity.getAuthor().actorId);
            }
            values.put(NoteTable.NOTE_OID, note.oid);
            values.put(NoteTable.ORIGIN_ID, note.origin.getId());
            if (nonEmptyOid(note.conversationOid)) {
                values.put(NoteTable.CONVERSATION_OID, note.conversationOid);
            }
            values.put(NoteTable.BODY, note.getBody());
            values.put(NoteTable.BODY_TO_SEARCH, note.getBodyToSearch());

            activity.getNote().addRecipientsFromBodyText(activity.getActor());
            updateInReplyTo(activity, values);
            for ( Actor actor : note.audience().getRecipients()) {
                updateObjActor(actor.update(activity.accountActor, activity.getActor()));
            }
            if (activity.getNote().audience().containsMe(execContext.getMyContext())
                    && !activity.isMyActorOrAuthor(execContext.myContext)) {
                values.put(NoteTable.MENTIONED, TriState.TRUE.id);
            }

            if (!TextUtils.isEmpty(note.via)) {
                values.put(NoteTable.VIA, note.via);
            }
            if (!TextUtils.isEmpty(note.url)) {
                values.put(NoteTable.URL, note.url);
            }
            if (note.getPrivate().known()) {
                values.put(NoteTable.PRIVATE, note.getPrivate().id);
            }

            if (note.lookupConversationId() != 0) {
                values.put(NoteTable.CONVERSATION_ID, note.getConversationId());
            }

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, ((note.noteId ==0) ? "insertMsg" : "updateMsg " + note.noteId)
                        + ":" + note.getStatus()
                        + (isFirstTimeLoaded ? " new;" : "")
                        + (isDraftUpdated ? " draft updated;" : "")
                        + (isNewerThanInDatabase ? " newer, updated at " + new Date(note.getUpdatedDate()) + ";"
                        : "") );
            }

            if (MyContextHolder.get().isTestRun()) {
                MyContextHolder.get().putAssertionData(MSG_ASSERTION_KEY, values);
            }
            if (note.noteId == 0) {
                Uri msgUri = execContext.getContext().getContentResolver().insert(
                        MatchedUri.getMsgUri(me.getActorId(), 0), values);
                note.noteId = ParsedUri.fromUri(msgUri).getNoteId();

                if (note.getConversationId() == 0) {
                    ContentValues values2 = new ContentValues();
                    values2.put(NoteTable.CONVERSATION_ID, note.setConversationIdFromMsgId());
                    execContext.getContext().getContentResolver().update(msgUri, values2, null, null);
                }
                MyLog.v("Note", "Added " + note);
            } else {
                Uri msgUri = MatchedUri.getMsgUri(me.getActorId(), note.noteId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
                MyLog.v("Note", "Updated " + note);
            }
            note.audience().save(execContext.getMyContext(), note.origin, note.noteId);

            if (isFirstTimeLoaded || isDraftUpdated) {
                saveAttachments(note);
            }

            if (keywordsFilter.matchedAny(note.getBodyToSearch())) {
                activity.setNotified(TriState.FALSE);
            } else {
                if (note.getStatus() == DownloadStatus.LOADED) {
                    execContext.getResult().incrementDownloadedCount();
                    execContext.getResult().incrementNewCount();
                }
            }
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    private void updateInReplyTo(AActivity activity, ContentValues values) {
        final AActivity inReply = activity.getNote().getInReplyTo();
        if (StringUtils.nonEmpty(inReply.getNote().oid)) {
            if (nonRealOid(inReply.getNote().conversationOid)) {
                inReply.getNote().setConversationOid(activity.getNote().conversationOid);
            }
            new DataUpdater(execContext).onActivity(inReply);
            if (inReply.getNote().noteId != 0) {
                activity.getNote().addRecipient(inReply.getAuthor());
                values.put(NoteTable.IN_REPLY_TO_NOTE_ID, inReply.getNote().noteId);
                if (inReply.getAuthor().actorId != 0) {
                    values.put(NoteTable.IN_REPLY_TO_ACTOR_ID, inReply.getAuthor().actorId);
                }
            }
        }
    }

    private void saveAttachments(Note note) {
        List<Long> downloadIds = new ArrayList<>();
        for (Attachment attachment : note.attachments) {
            DownloadData dd = DownloadData.getThisForNote(note.noteId, attachment.contentType, attachment.getUri());
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
        DownloadData.deleteOtherOfThisNote(note.noteId, downloadIds);
    }

    private void updateObjActor(AActivity activity) {
        Actor actor = activity.getObjActor();
        final String method = "updateObjActor";
        if (actor.isEmpty()) {
            MyLog.v(this, method + "; objActor is empty");
            return;
        }
        MyAccount me = execContext.getMyContext().accounts().fromActorOfSameOrigin(activity.accountActor);
        if (!me.isValid()) {
            if (activity.accountActor.equals(actor)) {
                MyLog.d(this, method +"; adding my account " + activity.accountActor);
            } else {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
        }

        TriState followedByMe = TriState.UNKNOWN;
        TriState followedByActor = activity.type.equals(ActivityType.FOLLOW) ? TriState.TRUE :
                activity.type.equals(ActivityType.UNDO_FOLLOW) ? TriState.FALSE : TriState.UNKNOWN;
        if (actor.followedByMe.known()) {
            followedByMe = actor.followedByMe;
        } else if (activity.getActor().actorId == me.getActorId() && me.getActorId() != 0) {
            followedByMe = followedByActor;
        }

        actor.lookupActorId();
        if (actor.actorId != 0 && actor.isPartiallyDefined() && followedByMe.unknown()) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + "; Skipping partially defined: " + actor.toString());
            }
            return;
        }
        actor.lookupUserId();

        String actorOid = (actor.actorId == 0 && !actor.isOidReal()) ? actor.getTempOid() : actor.oid;
        try {
            ContentValues values = new ContentValues();
            if (actor.actorId == 0 || !actor.isPartiallyDefined()) {
                if (actor.actorId == 0 || actor.isOidReal()) {
                    values.put(ActorTable.ACTOR_OID, actorOid);
                }

                // Substitute required empty values with some temporary for a new entry only!
                String username = actor.getUsername();
                if (SharedPreferencesUtil.isEmpty(username)) {
                    username = "id:" + actorOid;
                }
                values.put(ActorTable.USERNAME, username);
                String webFingerId = actor.getWebFingerId();
                if (SharedPreferencesUtil.isEmpty(webFingerId)) {
                    webFingerId = username;
                }
                values.put(ActorTable.WEBFINGER_ID, webFingerId);
                String realName = actor.getRealName();
                if (SharedPreferencesUtil.isEmpty(realName)) {
                    realName = username;
                }
                values.put(ActorTable.REAL_NAME, realName);
                // End of required attributes
            }

            if (!SharedPreferencesUtil.isEmpty(actor.avatarUrl)) {
                values.put(ActorTable.AVATAR_URL, actor.avatarUrl);
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getDescription())) {
                values.put(ActorTable.DESCRIPTION, actor.getDescription());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getHomepage())) {
                values.put(ActorTable.HOMEPAGE, actor.getHomepage());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getProfileUrl())) {
                values.put(ActorTable.PROFILE_URL, actor.getProfileUrl());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.bannerUrl)) {
                values.put(ActorTable.BANNER_URL, actor.bannerUrl);
            }
            if (!SharedPreferencesUtil.isEmpty(actor.location)) {
                values.put(ActorTable.LOCATION, actor.location);
            }
            if (actor.notesCount > 0) {
                values.put(ActorTable.NOTES_COUNT, actor.notesCount);
            }
            if (actor.favoritesCount > 0) {
                values.put(ActorTable.FAVORITES_COUNT, actor.favoritesCount);
            }
            if (actor.followingCount > 0) {
                values.put(ActorTable.FOLLOWING_COUNT, actor.followingCount);
            }
            if (actor.followersCount > 0) {
                values.put(ActorTable.FOLLOWERS_COUNT, actor.followersCount);
            }
            if (actor.getCreatedDate() > 0) {
                values.put(ActorTable.CREATED_DATE, actor.getCreatedDate());
            }
            if (actor.getUpdatedDate() > 0) {
                values.put(ActorTable.UPDATED_DATE, actor.getUpdatedDate());
            }

            if (followedByMe.known()) {
                values.put(FriendshipTable.FOLLOWED, followedByMe.toBoolean(false));
                MyLog.v(this, "Account '" + me.getAccountName() + "' "
                                + (followedByMe.toBoolean(false) ? "follows" : "stop following ")
                                + actor.getUsername());
            }
            
            // Construct the Uri to the Actor
            Uri actorUri = MatchedUri.getActorUri(me.getActorId(), actor.actorId);
            actor.saveUser(execContext.myContext);
            if (actor.actorId == 0) {
                values.put(ActorTable.ORIGIN_ID, actor.origin.getId());
                values.put(ActorTable.USER_ID, actor.userId);
                actor.actorId = ParsedUri.fromUri(
                        execContext.getContext().getContentResolver().insert(actorUri, values))
                        .getActorId();
            } else if (values.size() > 0) {
                execContext.getContext().getContentResolver().update(actorUri, values, null, null);
            }
            if (actor.hasLatestNote()) {
                updateNote(actor.getLatestActivity(), false);
            }
        } catch (Exception e) {
            MyLog.e(this, method + "; actorId=" + actor.actorId + "; oid=" + actorOid, e);
        }
        MyLog.v(this, method + "; actorId=" + actor.actorId + "; oid=" + actorOid);
    }

    public void downloadOneNoteBy(String actorOid) throws ConnectionException {
        List<AActivity> activities = execContext.getConnection().getTimeline(
                TimelineType.USER.getConnectionApiRoutine(), TimelinePosition.EMPTY,
                TimelinePosition.EMPTY, 1, actorOid);
        for (AActivity item : activities) {
            onActivity(item, false);
        }
        saveLum();
    }

}
