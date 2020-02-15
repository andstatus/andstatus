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

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.note.KeywordsFilter;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Date;
import java.util.List;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.andstatus.app.util.UriUtils.nonEmptyOid;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * Stores (updates) notes and actors
 *  from a Social network into a database.
 * 
 * @author yvolk@yurivolkov.com
 */
public class DataUpdater {
    public static final int MAX_RECURSING = 4;
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
        this(new CommandExecutionContext(
                ma.getOrigin().myContext.isEmptyOrExpired()
                    ? MyContextHolder.get()
                    : ma.getOrigin().myContext,
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma)
        ));
    }
    
    public DataUpdater(CommandExecutionContext execContext) {
        this.execContext = execContext;
    }

    public AActivity onActivity(AActivity mbActivity) {
        return onActivity(mbActivity, true);
    }

    public AActivity onActivity(AActivity activity, boolean saveLum) {
        return onActivityInternal(activity, saveLum, 0);
    }

    private AActivity onActivityInternal(AActivity activity, boolean saveLum, int recursing) {
        if (activity == null || activity.isEmpty() || recursing > MAX_RECURSING) {
            return activity;
        }
        updateObjActor(activity.accountActor.update(activity.getActor()), recursing + 1);
        switch (activity.getObjectType()) {
            case ACTIVITY:
                onActivityInternal(activity.getActivity(), false, recursing + 1);
                break;
            case NOTE:
                updateNote(activity, recursing + 1);
                break;
            case ACTOR:
                updateObjActor(activity, recursing + 1);
                break;
            default:
                throw new IllegalArgumentException("Unexpected activity: " + activity);
        }
        updateActivity(activity);
        if (saveLum && recursing == 0) {
            saveLum();
        }
        return activity;
    }

    private void updateActivity(AActivity activity) {
        if (activity.isSubscribedByMe().notFalse
            && activity.getUpdatedDate() > 0
            && (activity.isMyActorOrAuthor(execContext.myContext)
                || activity.getNote().audience().containsMe(execContext.myContext))) {
            activity.setSubscribedByMe(TriState.TRUE);
        }
        if (activity.isNotified().unknown && execContext.myContext.users().isMe(activity.getActor()) &&
                activity.getNote().getStatus().isPresentAtServer() &&
            MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()).isTrue) {
            activity.setNotified(TriState.FALSE);
        }
        activity.save(execContext.getMyContext());
        lum.onNewActorActivity(new ActorActivity(activity.getActor().actorId, activity.getId(), activity.getUpdatedDate()));
        if ( !activity.isAuthorActor()) {
            lum.onNewActorActivity(new ActorActivity(activity.getAuthor().actorId, activity.getId(), activity.getUpdatedDate()));
        }
        execContext.getResult().onNotificationEvent(activity.getNewNotificationEventType());
    }

    public void saveLum() {
        lum.save();
    }

    private void updateNote(@NonNull AActivity activity, int recursing) {
        if (recursing > MAX_RECURSING) return;

        updateNote1(activity, recursing);
        DataUpdater.onActivities(execContext, activity.getNote().replies);
    }

    private void updateNote1(@NonNull AActivity activity, int recursing) {
        final String method = "updateNote1";
        final Note note = activity.getNote();
        try {
            MyAccount me = execContext.myContext.accounts().fromActorOfSameOrigin(activity.accountActor);
            if (me.nonValid()) {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
            if (activity.isAuthorActor()) {
                activity.setAuthor(activity.getActor());
            } else {
                updateObjActor(activity.getActor().update(activity.accountActor, activity.getAuthor()), recursing + 1);
            }
            if (note.noteId == 0) {
                note.noteId = MyQuery.oidToId(OidEnum.NOTE_OID, note.origin.getId(), note.oid);
            }

            final long updatedDateStored;
            final DownloadStatus statusStored;
            if (note.noteId != 0) {
                statusStored = DownloadStatus.load(
                        MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note.noteId));
                updatedDateStored = MyQuery.noteIdToLongColumnValue(NoteTable.UPDATED_DATE, note.noteId);
            } else {
                updatedDateStored = 0;
                statusStored = DownloadStatus.ABSENT;
            }

            /*
             * Is the row first time retrieved from a Social Network?
             * Note can already exist in this these cases:
             * 1. There was only "a stub" stored (without a sent date and content)
             * 2. Note was "unsent" i.e. it had content, but didn't have oid
             */
            final boolean isFirstTimeLoaded = (note.getStatus() == DownloadStatus.LOADED || note.noteId == 0) &&
                    statusStored != DownloadStatus.LOADED;
            boolean isFirstTimeSent = !isFirstTimeLoaded && note.noteId != 0 &&
                    StringUtil.nonEmptyNonTemp(note.oid) &&
                    statusStored.isUnsentDraft() &&
                    StringUtil.isEmptyOrTemp(MyQuery.idToOid(execContext.myContext,
                            OidEnum.NOTE_OID, note.noteId, 0));
            if (note.getStatus() == DownloadStatus.UNKNOWN && isFirstTimeSent) {
                note.setStatus(DownloadStatus.SENT);
            }
            boolean isDraftUpdated = !isFirstTimeLoaded && !isFirstTimeSent && note.getStatus().isUnsentDraft();

            boolean isNewerThanInDatabase = note.getUpdatedDate() > updatedDateStored;
            if (!isFirstTimeLoaded && !isFirstTimeSent && !isDraftUpdated && !isNewerThanInDatabase) {
                MyLog.v("Note", () -> "Skipped note as not younger " + note);
                return;
            }

            // TODO: move as toContentValues() into Note
            ContentValues values = new ContentValues();
            if (isFirstTimeLoaded || note.noteId == 0) {
                values.put(NoteTable.INS_DATE, MyLog.uniqueCurrentTimeMS());
            }
            values.put(NoteTable.NOTE_STATUS, note.getStatus().save());
            if (isNewerThanInDatabase) {
                values.put(NoteTable.UPDATED_DATE, note.getUpdatedDate());
            }

            if (activity.getAuthor().actorId != 0) {
                values.put(NoteTable.AUTHOR_ID, activity.getAuthor().actorId);
            }
            if (nonEmptyOid(note.oid)) {
                values.put(NoteTable.NOTE_OID, note.oid);
            }
            values.put(NoteTable.ORIGIN_ID, note.origin.getId());
            if (nonEmptyOid(note.conversationOid)) {
                values.put(NoteTable.CONVERSATION_OID, note.conversationOid);
            }
            if (note.hasSomeContent()) {
                values.put(NoteTable.NAME, note.getName());
                values.put(NoteTable.SUMMARY, note.getSummary());
                values.put(NoteTable.SENSITIVE, note.isSensitive() ? 1 : 0);
                values.put(NoteTable.CONTENT, note.getContent());
                values.put(NoteTable.CONTENT_TO_SEARCH, note.getContentToSearch());
            }

            updateInReplyTo(activity, values);
            activity.getNote().audience().lookupUsers();
            for ( Actor actor : note.audience().getActors()) {
                updateObjActor(activity.getActor().update(activity.accountActor, actor), recursing + 1);
            }

            if (!StringUtil.isEmpty(note.via)) {
                values.put(NoteTable.VIA, note.via);
            }
            if (!StringUtil.isEmpty(note.url)) {
                values.put(NoteTable.URL, note.url);
            }
            if (note.getPublic().known) {
                values.put(NoteTable.PUBLIC, note.getPublic().id);
            }

            if (note.lookupConversationId() != 0) {
                values.put(NoteTable.CONVERSATION_ID, note.getConversationId());
            }
            if (note.getStatus().mayUpdateContent() && shouldSaveAttachments(isFirstTimeLoaded, isDraftUpdated)) {
                values.put(NoteTable.ATTACHMENTS_COUNT, note.attachments.size());
            }

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, () -> ((note.noteId ==0) ? "insertNote" : "updateNote " + note.noteId)
                        + ":" + note.getStatus()
                        + (isFirstTimeLoaded ? " new;" : "")
                        + (isDraftUpdated ? " draft updated;" : "")
                        + (isFirstTimeSent ? " just sent;" : "")
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
                MyLog.v("Note", () -> "Added " + note);
            } else {
                Uri msgUri = MatchedUri.getMsgUri(me.getActorId(), note.noteId);
                execContext.getContext().getContentResolver().update(msgUri, values, null, null);
                MyLog.v("Note", () -> "Updated " + note);
            }
            if (note.getStatus().mayUpdateContent()) {
                note.audience().save(execContext.getMyContext(), note.origin, note.noteId, note.getPublic(), false);

                if (shouldSaveAttachments(isFirstTimeLoaded, isDraftUpdated)) {
                    note.attachments.save(execContext, note.noteId);
                }

                if (keywordsFilter.matchedAny(note.getContentToSearch())) {
                    activity.setNotified(TriState.FALSE);
                } else {
                    if (note.getStatus() == DownloadStatus.LOADED) {
                        execContext.getResult().incrementDownloadedCount();
                        execContext.getResult().incrementNewCount();
                    }
                }
            }
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }
    }

    private boolean shouldSaveAttachments(boolean isFirstTimeLoaded, boolean isDraftUpdated) {
        return isFirstTimeLoaded || isDraftUpdated;
    }

    private void updateInReplyTo(AActivity activity, ContentValues values) {
        final AActivity inReply = activity.getNote().getInReplyTo();
        if (StringUtil.nonEmpty(inReply.getNote().oid)) {
            if (nonRealOid(inReply.getNote().conversationOid)) {
                inReply.getNote().setConversationOid(activity.getNote().conversationOid);
            }
            new DataUpdater(execContext).onActivity(inReply);
            if (inReply.getNote().noteId != 0) {
                activity.getNote().audience().add(inReply.getAuthor());
                values.put(NoteTable.IN_REPLY_TO_NOTE_ID, inReply.getNote().noteId);
                if (inReply.getAuthor().actorId != 0) {
                    values.put(NoteTable.IN_REPLY_TO_ACTOR_ID, inReply.getAuthor().actorId);
                }
            }
        }
    }

    public void updateObjActor(AActivity activity, int recursing) {
        if (recursing > MAX_RECURSING) return;

        Actor objActor = activity.getObjActor();
        final String method = "updateObjActor";
        if (objActor.dontStore()) {
            MyLog.v(this, () -> method + "; don't store: " + objActor.getUniqueName());
            return;
        }
        MyAccount me = execContext.getMyContext().accounts().fromActorOfSameOrigin(activity.accountActor);
        if (me.nonValid()) {
            if (activity.accountActor.equals(objActor)) {
                MyLog.d(this, method +"; adding my account " + activity.accountActor);
            } else {
                MyLog.w(this, method +"; my account is invalid, skipping: " + activity.toString());
                return;
            }
        }

        fixActorUpdatedDate(activity, objActor);
        objActor.lookupActorId();

        if (objActor.actorId != 0 && objActor.isPartiallyDefined() && objActor.isMyFriend.unknown
                && activity.followedByActor().unknown && objActor.groupType == GroupType.UNKNOWN) {
            MyLog.v(this, () -> method + "; Skipping partially defined: " + objActor);
            return;
        }

        objActor.lookupUser();
        if (shouldWeUpdateActor(method, objActor)) {
            updateObjActor2(activity, recursing, me);
        } else {
            updateFriendship(activity, me);
            execContext.myContext.users().reload(objActor);
        }

        MyLog.v(this, () -> method + "; " + objActor);
    }

    private boolean shouldWeUpdateActor(String method, Actor objActor) {
        long updatedDateStored = MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, objActor.actorId);
        if (updatedDateStored > SOME_TIME_AGO && updatedDateStored >= objActor.getUpdatedDate()) {
            MyLog.v(this, () -> method + "; Skipped actor update as not younger " + objActor);
            return false;
        }
        return true;
    }

    private void updateObjActor2(AActivity activity, int recursing, MyAccount me) {
        final String method = "updateObjActor2";
        try {
            Actor actor = activity.getObjActor();
            String actorOid = (actor.actorId == 0 && !actor.isOidReal()) ? actor.toTempOid() : actor.oid;

            ContentValues values = new ContentValues();
            if (actor.actorId == 0 || !actor.isPartiallyDefined()) {
                if (actor.actorId == 0 || actor.isOidReal()) {
                    values.put(ActorTable.ACTOR_OID, actorOid);
                }

                // Substitute required empty values with some temporary for a new entry only!
                String username = actor.getUsername();
                if (SharedPreferencesUtil.isEmpty(username)) {
                    username = StringUtil.toTempOid(actorOid);
                }
                values.put(ActorTable.USERNAME, username);
                values.put(ActorTable.WEBFINGER_ID, actor.getWebFingerId());
                String realName = actor.getRealName();
                if (SharedPreferencesUtil.isEmpty(realName)) {
                    realName = username;
                }
                values.put(ActorTable.REAL_NAME, realName);
                // End of required attributes
            }

            if (actor.groupType.isGroup.known) {
                values.put(ActorTable.GROUP_TYPE, actor.groupType.id);
            }
            if (actor.getParentActorId() != 0) {
                values.put(ActorTable.PARENT_ACTOR_ID, actor.getParentActorId());
            }
            if (actor.hasAvatar()) {
                values.put(ActorTable.AVATAR_URL, actor.getAvatarUrl());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getSummary())) {
                values.put(ActorTable.SUMMARY, actor.getSummary());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getHomepage())) {
                values.put(ActorTable.HOMEPAGE, actor.getHomepage());
            }
            if (!SharedPreferencesUtil.isEmpty(actor.getProfileUrl())) {
                values.put(ActorTable.PROFILE_PAGE, actor.getProfileUrl());
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

            actor.saveUser();
            Uri actorUri = MatchedUri.getActorUri(me.getActorId(), actor.actorId);
            if (actor.actorId == 0) {
                values.put(ActorTable.ORIGIN_ID, actor.origin.getId());
                values.put(ActorTable.USER_ID, actor.user.userId);
                actor.actorId = ParsedUri.fromUri(
                        execContext.getContext().getContentResolver().insert(actorUri, values))
                        .getActorId();
            } else if (values.size() > 0) {
                execContext.getContext().getContentResolver().update(actorUri, values, null, null);
            }
            actor.endpoints.save(actor.actorId);

            updateFriendship(activity, me);

            actor.avatarFile.resetAvatarErrors(execContext.myContext);
            execContext.myContext.users().reload(actor);

            if (actor.isPartiallyDefined()) {
                actor.requestDownload();
            }
            actor.requestAvatarDownload();
            if (actor.hasLatestNote()) {
                updateNote(actor.getLatestActivity(), recursing + 1);
            }
        } catch (Exception e) {
            MyLog.e(this, method + "; " + activity, e);
        }
    }

    private void updateFriendship(AActivity activity, MyAccount me) {
        Actor objActor = activity.getObjActor();
        if (objActor.isMyFriend.known) {
            MyLog.v(this, () -> "Account " + me.getActor().getUniqueNameWithOrigin() + " "
                    + (objActor.isMyFriend.isTrue ? "follows " : "stopped following ")
                    + objActor.getUniqueNameWithOrigin());
            GroupMembership.setMember(execContext.myContext, me.getActor(), GroupType.FRIENDS,
                    objActor.isMyFriend, objActor);
            execContext.myContext.users().reload(me.getActor());
        }
        if (activity.followedByActor().known) {
            MyLog.v(this, () -> "Actor " + activity.getActor().getUniqueNameWithOrigin() + " "
                    + (activity.followedByActor().isTrue ? "follows " : "stopped following ")
                    + objActor.getUniqueNameWithOrigin());
            GroupMembership.setMember(execContext.myContext, activity.getActor(), GroupType.FRIENDS,
                    activity.followedByActor(), objActor);
            execContext.myContext.users().reload(activity.getActor());
        }
    }

    private void fixActorUpdatedDate(AActivity activity, Actor actor) {
        if (actor.getCreatedDate() <= SOME_TIME_AGO && actor.getUpdatedDate() <= SOME_TIME_AGO) return;

        if (actor.getUpdatedDate() <= SOME_TIME_AGO
                || activity.type == ActivityType.FOLLOW
                || activity.type == ActivityType.UNDO_FOLLOW) {
            actor.setUpdatedDate(Math.max(activity.getUpdatedDate(), actor.getCreatedDate()));
        }
    }

    public void downloadOneNoteBy(Actor actor) throws ConnectionException {
        List<AActivity> activities = execContext.getConnection().getTimeline(
                TimelineType.SENT.getConnectionApiRoutine(), TimelinePosition.EMPTY,
                TimelinePosition.EMPTY, 1, actor);
        for (AActivity item : activities) {
            onActivity(item, false);
        }
        saveLum();
    }

}
