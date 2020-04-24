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
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;

import java.util.Set;
import java.util.stream.Collectors;

import io.vavr.control.Try;

class CommandExecutorOther extends CommandExecutorStrategy{
    private static final int ACTORS_LIMIT = 400;

    CommandExecutorOther(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public Try<Boolean> execute() {
        switch (execContext.getCommandData().getCommand()) {
            case LIKE:
            case UNDO_LIKE:
                return createOrDestroyFavorite(execContext.getCommandData().itemId,
                        execContext.getCommandData().getCommand() == CommandEnum.LIKE);
            case FOLLOW:
            case UNDO_FOLLOW:
                return followOrStopFollowingActor(getActor(),
                        execContext.getCommandData().getCommand() == CommandEnum.FOLLOW);
            case UPDATE_NOTE:
                return updateNote(execContext.getCommandData().itemId);
            case DELETE_NOTE:
                return deleteNote(execContext.getCommandData().itemId);
            case UNDO_ANNOUNCE:
                return undoAnnounce(execContext.getCommandData().itemId);
            case GET_CONVERSATION:
                return getConversation(execContext.getCommandData().itemId);
            case GET_NOTE:
                return getNote(execContext.getCommandData().itemId);
            case GET_ACTOR:
                return getActorCommand(getActor(), execContext.getCommandData().getUsername());
            case SEARCH_ACTORS:
                return searchActors(execContext.getCommandData().getUsername());
            case ANNOUNCE:
                return reblog(execContext.getCommandData().itemId);
            case RATE_LIMIT_STATUS:
                return rateLimitStatus();
            case GET_ATTACHMENT:
                return FileDownloader.newForDownloadData(execContext.myContext, DownloadData.fromId(execContext.getCommandData().itemId))
                        .setConnectionRequired(ConnectionRequired.DOWNLOAD_ATTACHMENT)
                        .load(execContext.getCommandData());
            case GET_AVATAR:
                return (new AvatarDownloader(getActor())).load(execContext.getCommandData());
            default:
                return TryUtils.failure("Unexpected command here " + execContext.getCommandData());
        }
    }

    private Try<Boolean> searchActors(String searchQuery) {
        final String method = "searchActors";
        String msgLog = method + "; query='" + searchQuery + "'";
        if (StringUtil.isEmpty(searchQuery)) {
            return logExecutionError(true, msgLog + ", empty query");
        }

        return getConnection()
        .searchActors(ACTORS_LIMIT, searchQuery)
        .map(actors -> {
            final DataUpdater dataUpdater = new DataUpdater(execContext);
            final Actor myAccountActor = execContext.getMyAccount().getActor();
            for (Actor actor : actors) {
                dataUpdater.onActivity(myAccountActor.update(actor));
            }
            return true;
        })
        .mapFailure(e -> ConnectionException.of(e).append(msgLog));
    }

    private Try<Boolean> getConversation(long noteId) {
        final String method = "getConversation";
        String conversationOid = MyQuery.noteIdToConversationOid(execContext.myContext, noteId);
        if (StringUtil.isEmpty(conversationOid)) {
            return logExecutionError(true, method + " empty conversationId " +
                    MyQuery.noteInfoForLog(execContext.myContext, noteId));
        }
        return getConnection()
        .getConversation(conversationOid)
        .onSuccess(activities -> {
            DataUpdater.onActivities(execContext, activities);
            MyLog.d(this, method + (noErrors() ? " succeeded" : " failed"));
        })
        .onSuccess(activities -> {
            Set<Long> noteIds = activities.stream()
                    .map(activity -> activity.getNote().noteId)
                    .collect(Collectors.toSet());
            if (noteIds.size() > 1) {
                if (new CheckConversations().setNoteIdsOfOneConversation(noteIds)
                        .setMyContext(execContext.myContext).fix() > 0) {
                    execContext.getCommandData().getResult().incrementNewCount();
                }
            }
        })
        .map(activities -> true)
        .mapFailure(e -> ConnectionException.of(e).append(MyQuery.noteInfoForLog(execContext.myContext, noteId)));
    }

    private Try<Boolean> getActorCommand(Actor actorIn, String username) {
        final String method = "getActor";
        String msgLog = method + ";";
        Actor actorIn2 = UriUtils.nonRealOid(actorIn.oid) && actorIn.origin.isUsernameValid(username)
                && !actorIn.isUsernameValid()
            ? Actor.fromId(actorIn.origin, actorIn.actorId).setUsername(username)
                .setWebFingerId(actorIn.getWebFingerId())
            : actorIn;
        if (!actorIn2.canGetActor()) {
            msgLog += ", cannot get Actor";
            return logExecutionError(true, msgLog + actorInfoLogged(actorIn2));
        }
        String msgLog2  = msgLog + "; username='" + actorIn2.getUsername() + "'";

        return getConnection()
        .getActor(actorIn2)
        .flatMap(actor ->
            failIfActorIsEmpty(msgLog2, actor))
        .map(actor -> {
            AActivity activity = execContext.getMyAccount().getActor().update(actor);
            new DataUpdater(execContext).onActivity(activity);
            return true;
        })
        .onFailure(e -> actorIn2.requestAvatarDownload());
    }

    /**
     * @param create true - create, false - destroy
     */
    private Try<Boolean> createOrDestroyFavorite(long noteId, boolean create) {
        final String method = (create ? "create" : "destroy") + "Favorite";
        return getNoteOid(method, noteId, true)
        .flatMap( oid -> create
            ? getConnection().like(oid)
            : getConnection().undoLike(oid)
        )
        .flatMap(activity ->
                failIfEmptyNote(method, noteId, activity.getNote())
                .map(b -> activity))
        .flatMap(activity -> {
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
                    AActivity activity2 = activity.getNote().act(activity.accountActor, activity.getActor(), ActivityType.LIKE);
                    MyLog.d(this, method + "; Favorited flag didn't change yet.");
                    // Let's try to assume that everything was OK
                    return Try.success(activity2);
                } else {
                    // yvolk: 2011-09-27 Sometimes this
                    // twitter.com 'async' process doesn't work
                    // so let's try another time...
                    // This is safe, because "delete favorite"
                    // works even for the "Unfavorited" tweet :-)
                    return logExecutionError(false, method + "; Favorited flag didn't change yet. " +
                            MyQuery.noteInfoForLog(execContext.myContext, noteId));
                }
            }
            return Try.success(activity);
        })
        .map(activity -> {
            // Please note that the Favorited note may be NOT in the Account's Home timeline!
            new DataUpdater(execContext).onActivity(activity);
            return true;
        });
    }

    @NonNull
    private Try<String> getNoteOid(String method, long noteId, boolean required) {
        String oid = MyQuery.idToOid(execContext.myContext, OidEnum.NOTE_OID, noteId, 0);
        if (required && StringUtil.isEmpty(oid)) {
            return logExecutionError(true, method + "; no note ID in the Social Network "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId));
        }
        return Try.success(oid);
    }

    /**
     * @param follow true - Follow, false - Stop following
     */
    private Try<Boolean> followOrStopFollowingActor(Actor actor, boolean follow) {
        final String method = (follow ? "follow" : "stopFollowing") + "Actor";
        return getConnection()
        .follow(actor.oid, follow)
        .flatMap(activity -> {
            final Actor friend = activity.getObjActor();
            friend.isMyFriend = TriState.UNKNOWN; // That "hack" attribute may only confuse us here as it can show outdated info
            return failIfActorIsEmpty(method, friend)
                .map(a -> {
                    new DataUpdater(execContext).onActivity(activity);
                    return true;
                });
        });
    }

    private Try<Actor> failIfActorIsEmpty(String method, Actor actor) {
        if (actor == null || actor.isEmpty()) {
            return logExecutionError(false, "Actor is empty, " + method);
        }
        return Try.success(actor);
    }

    private String actorInfoLogged(Actor actor) {
        return actor.toString();
    }

    private Try<Boolean> deleteNote(long noteId) {
        final String method = "deleteNote";
        if (noteId == 0) {
            MyLog.d(this, method + " skipped as noteId == 0");
            return Try.success(true);
        }
        Actor author = Actor.load(execContext.getMyContext(), MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, noteId));
        return (execContext.getMyAccount().getActor().isSame(author)
                 ? deleteNoteAtServer(noteId, method)
                 : Try.success(true))
        .onSuccess(b -> MyProvider.deleteNoteAndItsActivities(execContext.getMyContext(), noteId));
    }

    private Try<Boolean> deleteNoteAtServer(long noteId, String method) {
        Try<String> tryOid = getNoteOid(method, noteId, false);
        DownloadStatus statusStored = DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId));
        if (tryOid.filter(StringUtil::nonEmptyNonTemp).isFailure() || statusStored != DownloadStatus.LOADED) {
            MyLog.i(this, method + "; OID='" + tryOid + "', status='" + statusStored + "' for noteId=" + noteId);
            return Try.success(true);
        }
        return tryOid
            .flatMap(oid -> getConnection().deleteNote(oid))
            .recoverWith(ConnectionException.class, e ->
                // "Not found" means that there is no such "Status", so we may
                // assume that it's Ok!
                (e.getStatusCode() == StatusCode.NOT_FOUND)
                        ? Try.success(true)
                        : logException(e, method + "; noteOid:" + tryOid + ", " +
                        MyQuery.noteInfoForLog(execContext.myContext, noteId)).map(any -> true)
            );
    }

    private Try<Boolean> undoAnnounce(long noteId) {
        final String method = "destroyReblog";
        final long actorId = execContext.getMyAccount().getActorId();
        final Pair<Long, ActivityType> reblogAndType = MyQuery.noteIdToLastReblogging(
                execContext.getMyContext().getDatabase(), noteId, actorId);
        if (reblogAndType.second != ActivityType.ANNOUNCE) {
            return logExecutionError(true, "No local Reblog of "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId) +
                    " by " + execContext.getMyAccount() );
        }
        String reblogOid = MyQuery.idToOid(execContext.myContext, OidEnum.REBLOG_OID, noteId, actorId);
        return getConnection()
        .undoAnnounce(reblogOid)
        .recoverWith(ConnectionException.class, e ->
            // "Not found" means that there is no such "Status", so we may
            // assume that it's Ok!
            (e.getStatusCode() == StatusCode.NOT_FOUND)
                ? Try.success(true)
                : logException(e, method + "; reblogOid:" + reblogOid + ", " +
                MyQuery.noteInfoForLog(execContext.myContext, noteId)).map(any -> true)
        )
        .onSuccess(b ->
            // And delete the reblog from local storage
            MyProvider.deleteActivity(execContext.getMyContext(), reblogAndType.first, noteId, false)
        );
    }

    private Try<Boolean> getNote(long noteId) {
        final String method = "getNote";
        return getNoteOid(method, noteId, true)
        .flatMap(oid -> getConnection().getNote(oid))
        .flatMap(activity -> {
            if (activity.isEmpty()) {
                return logExecutionError(false, "Received Note is empty, "
                        + MyQuery.noteInfoForLog(execContext.myContext, noteId));
            } else {
                try {
                    new DataUpdater(execContext).onActivity(activity);
                    return Try.success(true);
                } catch (Exception e) {
                    return logExecutionError(false, "Error while saving to the local cache,"
                            + MyQuery.noteInfoForLog(execContext.myContext, noteId) + ", " + e.getMessage());
                }
            }
        })
        .onFailure(e -> {
            if (ConnectionException.of(e).getStatusCode() == StatusCode.NOT_FOUND) {
                execContext.getResult().incrementParseExceptions();
                // This means that there is no such "Status"
                // TODO: so we don't need to retry this command
            }
        });
    }

    private Try<Boolean> updateNote(long activityId) {
        final String method = "updateNote";
        long noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, activityId);
        Note note = Note.loadContentById(execContext.myContext, noteId);
        DemoData.crashTest(() -> note.getContent().startsWith("Crash me on sending 2015-04-10"));

        String content = note.getContentToPost();
        // TODO: Add attachments to Note right here
        Attachments attachments = Attachments.load(execContext.myContext, noteId);
        String msgLog = (StringUtil.nonEmpty(note.getName()) ? "name:'" + note.getName() + "'; " : "")
                + (StringUtil.nonEmpty(note.getSummary()) ? "summary:'" + note.getSummary() + "'; " : "")
                + (StringUtil.nonEmpty(content) ? "content:'" + MyLog.trimmedString(content, 80) + "'" : "")
                + (attachments.isEmpty() ? "" : "; " + attachments);

        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, () -> method + ";" + msgLog);
        }
        if (!note.getStatus().mayBeSent()) {
            return Try.failure(ConnectionException.hardConnectionException("Wrong note status: " + note.getStatus(), null));
        }
        long inReplyToNoteId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId);
        Try<String> inReplyToNoteOid = getNoteOid(method, inReplyToNoteId, false);

        return inReplyToNoteOid
        .flatMap(oid -> getConnection().updateNote(note, oid, attachments))
        .flatMap(activity ->
            failIfEmptyNote(method, noteId, activity.getNote()).map(b -> {
                // The note was sent successfully, so now update unsent message
                // New Actor's note should be put into the Account's Home timeline.
                activity.setId(activityId);
                activity.getNote().noteId = noteId;
                new DataUpdater(execContext).onActivity(activity);
                execContext.getResult().setItemId(noteId);
                return true;
            })
            .onFailure( e -> execContext.getMyContext().getNotifier().onUnsentActivity(activityId))
        );
    }

    private Try<Boolean> failIfEmptyNote(String method, long noteId, Note note) {
        if (note == null || note.isEmpty()) {
            return logExecutionError(false, method + "; Received note is empty, "
                    + MyQuery.noteInfoForLog(execContext.myContext, noteId));
        }
        return Try.success(true);
    }

    private Try<Boolean> reblog(long rebloggedNoteId) {
        final String method = "Reblog";
        return getNoteOid(method, rebloggedNoteId, true)
        .flatMap(oid -> getConnection().announce(oid))
        .map(activity -> {
            failIfEmptyNote(method, rebloggedNoteId, activity.getNote());
            // The tweet was sent successfully
            // Reblog should be put into the Account's Home timeline!
            new DataUpdater(execContext).onActivity(activity);
            MyProvider.updateNoteReblogged(execContext.getMyContext(), activity.accountActor.origin, rebloggedNoteId);
            return true;
        });
    }
    
    private Try<Boolean> rateLimitStatus() {
        return getConnection().rateLimitStatus()
        .map(rateLimitStatus -> {
            if (rateLimitStatus.nonEmpty()) {
                execContext.getResult().setRemainingHits(rateLimitStatus.remaining);
                execContext.getResult().setHourlyLimit(rateLimitStatus.limit);
            }
            return rateLimitStatus.nonEmpty();
        });
    }
}
