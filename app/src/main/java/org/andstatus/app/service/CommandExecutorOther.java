/* 
 * Copyright (c) 2011-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.andstatus.app.context.DemoData;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.checker.CheckConversations;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.RateLimitStatus;
import org.andstatus.app.support.java.util.function.SupplierWithException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class CommandExecutorOther extends CommandExecutorStrategy{

    public static final int ACTORS_LIMIT = 400;

    CommandExecutorOther(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public void execute() {
        switch (execContext.getCommandData().getCommand()) {
            case LIKE:
            case UNDO_LIKE:
                createOrDestroyFavorite(execContext.getCommandData().itemId, 
                        execContext.getCommandData().getCommand() == CommandEnum.LIKE);
                break;
            case FOLLOW:
            case UNDO_FOLLOW:
                followOrStopFollowingActor(getActor(),
                        execContext.getCommandData().getCommand() == CommandEnum.FOLLOW);
                break;
            case UPDATE_NOTE:
                updateNote(execContext.getCommandData().itemId);
                break;
            case DELETE_NOTE:
                deleteNote(execContext.getCommandData().itemId);
                break;
            case UNDO_ANNOUNCE:
                undoAnnounce(execContext.getCommandData().itemId);
                break;
            case GET_CONVERSATION:
                getConversation(execContext.getCommandData().itemId);
                break;
            case GET_NOTE:
                getNote(execContext.getCommandData().itemId);
                break;
            case GET_ACTOR:
                getActorCommand(getActor(), execContext.getCommandData().getUsername());
                break;
            case SEARCH_ACTORS:
                searchActors(execContext.getCommandData().getUsername());
                break;
            case ANNOUNCE:
                reblog(execContext.getCommandData().itemId);
                break;
            case RATE_LIMIT_STATUS:
                rateLimitStatus();
                break;
            case GET_ATTACHMENT:
                FileDownloader.newForDownloadData(DownloadData.fromId(execContext.getCommandData().itemId))
                        .load(execContext.getCommandData());
                break;
            case GET_AVATAR:
                (new AvatarDownloader(getActor())).load(execContext.getCommandData());
                break;
            default:
                MyLog.e(this, "Unexpected command here " + execContext.getCommandData());
                break;
        }
    }

    private void searchActors(String searchQuery) {
        final String method = "searchActors";
        String msgLog = method + "; query='" + searchQuery + "'";
        List<Actor> actors = null;
        if (StringUtils.nonEmpty(searchQuery)) {
            try {
                final DataUpdater dataUpdater = new DataUpdater(execContext);
                final Actor myAccountActor = execContext.getMyAccount().getActor();
                actors = getConnection().searchActors(ACTORS_LIMIT, searchQuery);
                for (Actor actor : actors) {
                    dataUpdater.onActivity(myAccountActor.update(actor));
                }
            } catch (ConnectionException e) {
                logConnectionException(e, msgLog);
            }
        } else {
            msgLog += ", empty query";
            logExecutionError(true, msgLog);
        }
        MyLog.d(this, (msgLog + (noErrors() ? " succeeded" : " failed") ));
    }

    private void getConversation(long noteId) {
        final String method = "getConversation";
        String conversationOid = MyQuery.noteIdToConversationOid(noteId);
        if (StringUtils.isEmpty(conversationOid)) {
            logExecutionError(true, method + " empty conversationId " + MyQuery.noteInfoForLog(noteId));
        } else {
            Set<Long> noteIds = onActivities(method,
                    () -> getConnection().getConversation(conversationOid),
                    () -> MyQuery.noteInfoForLog(noteId))
                    .stream().map(activity -> activity.getNote().noteId).collect(Collectors.toSet());
            if (noteIds.size() > 1) {
                if (new CheckConversations().setNoteIdsOfOneConversation(noteIds)
                        .setMyContext(execContext.myContext).fix() > 0) {
                    execContext.getCommandData().getResult().incrementNewCount();
                }
            }
        }
    }

    private List<AActivity> onActivities(String method, SupplierWithException<List<AActivity>, ConnectionException> supplier,
                              Supplier<String> contextInfoSupplier) {
        List<AActivity> activities;
        try {
            activities = supplier.get();
            DataUpdater.onActivities(execContext, activities);
            MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
            return activities;
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                execContext.getResult().incrementParseExceptions();
            }
            logConnectionException(e, method + "; " + contextInfoSupplier.get());
        }
        return Collections.emptyList();
    }

    private void getActorCommand(Actor actorIn, String username) {
        final String method = "getActor";
        String msgLog = method + "; username='" + username + "'";
        Actor actor = null;
        if (UriUtils.isRealOid(actorIn.oid) || !StringUtils.isEmpty(username)) {
            try {
                actor = getConnection().getActor(actorIn.oid, username);
                logIfActorIsEmpty(msgLog, actorIn.actorId, actor);
            } catch (ConnectionException e) {
                logConnectionException(e, msgLog + actorInfoLogged(actorIn.actorId));
            }
        } else {
            msgLog += ", invalid actor IDs";
            logExecutionError(true, msgLog + actorInfoLogged(actorIn.actorId));
        }
        if (noErrors() && actor != null) {
            new DataUpdater(execContext).onActivity(execContext.getMyAccount().getActor().update(actor));
        }
        MyLog.d(this, (msgLog + (noErrors() ? " succeeded" : " failed") ));
    }

    /**
     * @param create true - create, false - destroy
     */
    private void createOrDestroyFavorite(long noteId, boolean create) {
        final String method = (create ? "create" : "destroy") + "Favorite";
        String oid = getNoteOid(method, noteId, true);
        AActivity activity = null;
        if (noErrors()) {
            try {
                if (create) {
                    activity = getConnection().like(oid);
                } else {
                    activity = getConnection().undoLike(oid);
                }
                logIfEmptyNote(method, noteId, activity.getNote());
            } catch (ConnectionException e) {
                logConnectionException(e, method + "; " + MyQuery.noteInfoForLog(noteId));
            }
        }
        if (noErrors() && activity != null) {
            if (!activity.type.equals(create ? ActivityType.LIKE : ActivityType.UNDO_LIKE)) {
                /*
                 * yvolk: 2011-09-27 Twitter docs state that
                 * this may happen due to asynchronous nature of
                 * the process, see
                 * https://dev.twitter.com/docs/
                 * api/1/post/favorites/create/%3Aid
                 */
                if (create) {
                    // For the case we created favorite, let's
                    // change the flag manually.
                    activity = activity.getNote().act(activity.accountActor, activity.getActor(), ActivityType.LIKE);

                    MyLog.d(this, method + "; Favorited flag didn't change yet.");
                    // Let's try to assume that everything was OK
                } else {
                    // yvolk: 2011-09-27 Sometimes this
                    // twitter.com 'async' process doesn't work
                    // so let's try another time...
                    // This is safe, because "delete favorite"
                    // works even for the "Unfavorited" tweet :-)
                    logExecutionError(false, method + "; Favorited flag didn't change yet. " + MyQuery.noteInfoForLog(noteId));
                }
            }

            if (noErrors()) {
                // Please note that the Favorited note may be NOT in the Account's Home timeline!
                new DataUpdater(execContext).onActivity(activity);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    @NonNull
    private String getNoteOid(String method, long noteId, boolean required) {
        String oid = MyQuery.idToOid(OidEnum.NOTE_OID, noteId, 0);
        if (required && StringUtils.isEmpty(oid)) {
            logExecutionError(true, method + "; no note ID in the Social Network "
                    + MyQuery.noteInfoForLog(noteId));
        }
        return oid;
    }

    /**
     * @param follow true - Follow, false - Stop following
     */
    private void followOrStopFollowingActor(Actor actor, boolean follow) {
        final String method = (follow ? "follow" : "stopFollowing") + "Actor";
        AActivity activity = null;
        if (noErrors()) {
            try {
                activity = getConnection().follow(actor.oid, follow);
                final Actor friend = activity.getObjActor();
                friend.followedByMe = TriState.UNKNOWN; // That "hack" attribute may only confuse us here as it can show outdated info
                logIfActorIsEmpty(method, actor.actorId, friend);
            } catch (ConnectionException e) {
                logConnectionException(e, method + actorInfoLogged(actor.actorId));
            }
        }
        if (activity != null && noErrors()) {
            if (noErrors()) {
                new DataUpdater(execContext).onActivity(activity);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfActorIsEmpty(String method, long actorId, Actor actor) {
        if (actor == null || actor.isEmpty()) {
            logExecutionError(false, "Received Actor is empty, " + method + actorInfoLogged(actorId));
        }
    }

    @NonNull
    private String getActorOid(String method, long actorId, boolean required) {
        String oid = MyQuery.idToOid(OidEnum.ACTOR_OID, actorId, 0);
        if (required && StringUtils.isEmpty(oid)) {
            logExecutionError(true, method + "; no Actor ID in the Social Network " + actorInfoLogged(actorId));
        }
        return oid;
    }

    private String actorInfoLogged(long actorId) {
        String oid = getActorOid("actorInfoLogged", actorId, false);
        return " actorId=" + actorId + ", oid" + (StringUtils.isEmpty(oid) ? " is empty" : "'" + oid + "'" +
                ", webFingerId:'" + MyQuery.actorIdToWebfingerId(actorId) + "'");
    }

    private void deleteNote(long noteId) {
        final String method = "deleteNote";
        boolean ok = false;
        String oid = getNoteOid(method, noteId, false);
        DownloadStatus statusStored = DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId));
        try {
            if (noteId == 0 || StringUtils.isEmpty(oid) || statusStored != DownloadStatus.LOADED) {
                ok = true;
                MyLog.i(this, method + "; OID='" + oid + "', status='" + statusStored + "' for noteId=" + noteId);
            } else {
                ok = getConnection().deleteNote(oid);
                logOk(ok);
            }
        } catch (ConnectionException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                // This means that there is no such "Status", so we may
                // assume that it's Ok!
                ok = true;
            } else {
                logConnectionException(e, method + "; " + oid);
            }
        }
        if (ok && noteId != 0) {
            MyProvider.deleteNote(execContext.getContext(), noteId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void undoAnnounce(long noteId) {
        final String method = "destroyReblog";
        final long actorId = execContext.getMyAccount().getActorId();
        final Pair<Long, ActivityType> reblogAndType = MyQuery.noteIdToLastReblogging(
                execContext.getMyContext().getDatabase(), noteId, actorId);
        if (reblogAndType.second != ActivityType.ANNOUNCE) {
            logExecutionError(true, "No local Reblog of "
                    + MyQuery.noteInfoForLog(noteId) + " by " + execContext.getMyAccount() );
            return;
        }
        String reblogOid = MyQuery.idToOid(OidEnum.REBLOG_OID, noteId, actorId);
        try {
            if (!getConnection().undoAnnounce(reblogOid)) {
                logExecutionError(false, "Connection returned 'false' " + method
                        + MyQuery.noteInfoForLog(noteId));
            }
        } catch (ConnectionException e) {
            // "Not found" means that there is no such "Status", so we may
            // assume that it's Ok!
            if (e.getStatusCode() != StatusCode.NOT_FOUND) {
                logConnectionException(e, method + "; reblogOid:" + reblogOid + ", " + MyQuery.noteInfoForLog(noteId));
            }
        }
        if (noErrors()) {
            try {
                // And delete the reblog from local storage
                MyProvider.deleteActivity(execContext.getMyContext(), reblogAndType.first, noteId, false);
            } catch (Exception e) {
                MyLog.e(this, "Error destroying reblog locally", e);
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void getNote(long noteId) {
        final String method = "getNote";
        String oid = getNoteOid(method, noteId, true);
        if (noErrors()) {
            try {
                AActivity activity = getConnection().getNote(oid);
                if (activity.isEmpty()) {
                    logExecutionError(false, "Received Note is empty, "
                            + MyQuery.noteInfoForLog(noteId));
                } else {
                    try {
                        new DataUpdater(execContext).onActivity(activity);
                    } catch (Exception e) {
                        logExecutionError(false, "Error while saving to the local cache,"
                                + MyQuery.noteInfoForLog(noteId) + ", " + e.getMessage());
                    }
                }
            } catch (ConnectionException e) {
                if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                    execContext.getResult().incrementParseExceptions();
                    // This means that there is no such "Status"
                    // TODO: so we don't need to retry this command
                }
                logConnectionException(e, method + MyQuery.noteInfoForLog(noteId));
            }
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void updateNote(long activityId) {
        final String method = "updateNote";
        long noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, activityId);
        Note note = Note.loadContentById(execContext.myContext, noteId);
        DemoData.crashTest(() -> note.getContent().startsWith("Crash me on sending 2015-04-10"));

        String content = note.getContentToPost();
        Audience audience = Audience.fromNoteId(note.origin, noteId);
        Uri mediaUri = DownloadData.getSingleAttachment(noteId).mediaUriToBePosted();
        String msgLog = (StringUtils.nonEmpty(note.getName()) ? "name:'" + note.getName() + "'" : "")
                + (StringUtils.nonEmpty(content) ? "content:'" + MyLog.trimmedString(content, 80) + "'" : "")
                + (mediaUri.equals(Uri.EMPTY) ? "" : "; mediaUri:'" + mediaUri + "'");

        AActivity activity = AActivity.EMPTY;
        try {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, () -> method + ";" + msgLog);
            }
            if (!note.getStatus().mayBeSent()) {
                throw ConnectionException.hardConnectionException("Wrong note status: " + note.getStatus(), null);
            }
            long inReplyToNoteId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId);
            String inReplyToNoteOid = getNoteOid(method, inReplyToNoteId, false);
            activity = getConnection().updateNote(note.getName(), content, note.oid, audience, inReplyToNoteOid, mediaUri);
            logIfEmptyNote(method, noteId, activity.getNote());
        } catch (ConnectionException e) {
            logConnectionException(e, method + "; " + msgLog);
        }
        if (noErrors() && activity.nonEmpty()) {
            // The note was sent successfully, so now update unsent message
            // New Actor's note should be put into the Account's Home timeline.
            activity.setId(activityId);
            activity.getNote().noteId = noteId;
            new DataUpdater(execContext).onActivity(activity);
            execContext.getResult().setItemId(noteId);
        } else {
            execContext.getMyContext().getNotifier().onUnsentActivity(activityId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }

    private void logIfEmptyNote(String method, long noteId, Note note) {
        if (note == null || note.isEmpty()) {
            logExecutionError(false, method + "; Received note is empty, "
                    + MyQuery.noteInfoForLog(noteId));
        }
    }

    private void reblog(long rebloggedNoteId) {
        final String method = "Reblog";
        String oid = getNoteOid(method, rebloggedNoteId, true);
        AActivity activity = AActivity.EMPTY;
        if (noErrors()) {
            try {
                activity = getConnection().announce(oid);
                logIfEmptyNote(method, rebloggedNoteId, activity.getNote());
            } catch (ConnectionException e) {
                logConnectionException(e, "Reblog " + oid);
            }
        }
        if (noErrors()) {
            // The tweet was sent successfully
            // Reblog should be put into the Account's Home timeline!
            new DataUpdater(execContext).onActivity(activity);
            MyProvider.updateNoteReblogged(execContext.getMyContext(), activity.accountActor.origin, rebloggedNoteId);
        }
        MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
    }
    
    private void rateLimitStatus() {
        try {
            RateLimitStatus rateLimitStatus = getConnection().rateLimitStatus();
            boolean ok = !rateLimitStatus.isEmpty();
            if (ok) {
                execContext.getResult().setRemainingHits(rateLimitStatus.remaining); 
                execContext.getResult().setHourlyLimit(rateLimitStatus.limit);
             }
            logOk(ok);
        } catch (ConnectionException e) {
            logConnectionException(e, "rateLimitStatus");
        }
    }
}
