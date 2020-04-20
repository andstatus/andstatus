/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.text.Spannable;

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFiles;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.SpanUtil;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.andstatus.app.timeline.DuplicationLink.DUPLICATES;
import static org.andstatus.app.timeline.DuplicationLink.IS_DUPLICATED;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public abstract class BaseNoteViewItem<T extends BaseNoteViewItem<T>> extends ViewItem<T> {
    private static final int MIN_LENGTH_TO_COMPARE = 5;
    MyContext myContext = MyContextHolder.get();
    long activityUpdatedDate = 0;

    public DownloadStatus noteStatus = DownloadStatus.UNKNOWN;

    private long activityId;
    private long noteId;
    private Origin origin = Origin.EMPTY;

    protected ActorViewItem author = ActorViewItem.EMPTY;

    Visibility visibility = Visibility.UNKNOWN;
    boolean isSensitive = false;
    Audience audience = Audience.EMPTY;

    public long inReplyToNoteId = 0;
    ActorViewItem inReplyToActor = ActorViewItem.EMPTY;

    String noteSource = "";

    private String nameString = "";
    private Spannable name = SpanUtil.EMPTY;
    private String summaryString = "";
    private Spannable summary = SpanUtil.EMPTY;
    private String contentString = "";
    private Spannable content = SpanUtil.EMPTY;
    String contentToSearch = "";

    boolean favorited = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    final long attachmentsCount;
    final AttachedImageFiles attachedImageFiles;

    private MyAccount linkedMyAccount = MyAccount.EMPTY;
    public final StringBuilder detailsSuffix = new StringBuilder();

    protected BaseNoteViewItem(boolean isEmpty, long updatedDate) {
        super(isEmpty, updatedDate);
        attachmentsCount = 0;
        attachedImageFiles = AttachedImageFiles.EMPTY;
    }

    BaseNoteViewItem(MyContext myContext, Cursor cursor) {
        super(false, DbUtils.getLong(cursor, NoteTable.UPDATED_DATE));
        activityId = DbUtils.getLong(cursor, ActivityTable.ACTIVITY_ID);
        setNoteId(DbUtils.getLong(cursor, ActivityTable.NOTE_ID));
        setOrigin(myContext.origins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID)));
        isSensitive = DbUtils.getBoolean(cursor, NoteTable.SENSITIVE);
        this.myContext = myContext;

        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            attachmentsCount = DbUtils.getLong(cursor, NoteTable.ATTACHMENTS_COUNT);
            attachedImageFiles = (attachmentsCount) == 0
                    ? AttachedImageFiles.EMPTY
                    : AttachedImageFiles.load(myContext, noteId);
        } else {
            attachmentsCount = 0;
            attachedImageFiles = AttachedImageFiles.EMPTY;
        }
    }

    void setOtherViewProperties(Cursor cursor) {
        setName(DbUtils.getString(cursor, NoteTable.NAME));
        setSummary(DbUtils.getString(cursor, NoteTable.SUMMARY));
        setContent(DbUtils.getString(cursor, NoteTable.CONTENT));

        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
        inReplyToActor = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID));
        visibility = Visibility.fromCursor(cursor);
        audience = Audience.fromNoteId(getOrigin(), getNoteId(), visibility);
        noteStatus = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));
        favorited = DbUtils.getTriState(cursor, NoteTable.FAVORITED) == TriState.TRUE;
        reblogged = DbUtils.getTriState(cursor, NoteTable.REBLOGGED) == TriState.TRUE;

        String via = DbUtils.getString(cursor, NoteTable.VIA);
        if (!StringUtil.isEmpty(via)) {
            noteSource = Html.fromHtml(via).toString().trim();
        }

        for (Actor actor : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), getOrigin(), getNoteId())) {
            rebloggers.put(actor.actorId, actor.getWebFingerId());
        }
    }

    @NonNull
    public MyContext getMyContext() {
        return myContext;
    }

    public void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getNoteId() {
        return noteId;
    }

    void setNoteId(long noteId) {
        this.noteId = noteId;
    }

    public ActorViewItem getAuthor() {
        return author;
    }

    public Origin getOrigin() {
        return origin;
    }

    void setOrigin(@NonNull Origin origin) {
        this.origin = origin;
    }

    void setLinkedAccount(long linkedActorId) {
        linkedMyAccount = getMyContext().accounts().fromActorId(linkedActorId);
    }

    @NonNull
    public MyAccount getLinkedMyAccount() {
        return linkedMyAccount;
    }

    private void setCollapsedStatus(MyStringBuilder noteDetails) {
        if (isCollapsed()) {
            noteDetails.withSpace("(+" + getChildrenCount() + ")");
        }
    }

    @Override
    @NonNull
    public DuplicationLink duplicates(Timeline timeline, Origin preferredOrigin, @NonNull T other) {
        if (isEmpty() || other.isEmpty()) return DuplicationLink.NONE;
        return (getNoteId() == other.getNoteId())
                ? duplicatesByFavoritedAndReblogged(preferredOrigin, other)
                : duplicatesByOther(preferredOrigin, other);
    }

    @NonNull
    private DuplicationLink duplicatesByFavoritedAndReblogged(Origin preferredOrigin, @NonNull T other) {
        if (favorited != other.favorited) {
            return favorited ? IS_DUPLICATED : DUPLICATES;
        } else if (reblogged != other.reblogged) {
            return reblogged ? IS_DUPLICATED : DUPLICATES;
        }
        if (preferredOrigin.nonEmpty()
                && !author.getActor().origin.equals(other.author.getActor().origin)) {
            if (preferredOrigin.equals(author.getActor().origin)) return IS_DUPLICATED;
            if (preferredOrigin.equals(other.author.getActor().origin)) return DUPLICATES;
        }
        if (!getLinkedMyAccount().equals(other.getLinkedMyAccount())) {
            return getLinkedMyAccount().compareTo(other.getLinkedMyAccount()) <= 0
                    ? IS_DUPLICATED : DUPLICATES;
        }
        return rebloggers.size() > other.rebloggers.size() ? IS_DUPLICATED : DUPLICATES;
    }

    @NonNull
    private DuplicationLink duplicatesByOther(Origin preferredOrigin, @NonNull T other) {
        if (updatedDate > SOME_TIME_AGO && other.updatedDate > SOME_TIME_AGO
              &&  (Math.abs(updatedDate - other.updatedDate) >= TimeUnit.HOURS.toMillis(24))
                || isTooShortToCompare()
                || other.isTooShortToCompare()
                ) return DuplicationLink.NONE;
        if (contentToSearch.equals(other.contentToSearch)) {
            if (updatedDate == other.updatedDate) {
                return duplicatesByFavoritedAndReblogged(preferredOrigin, other);
            } else if (updatedDate < other.updatedDate) {
                return IS_DUPLICATED;
            } else {
                return DUPLICATES;
            }
        } else if (contentToSearch.contains(other.contentToSearch)) {
            return DUPLICATES;
        } else if (other.contentToSearch.contains(contentToSearch)) {
            return IS_DUPLICATED;
        }
        return DuplicationLink.NONE;
    }

    boolean isTooShortToCompare() {
        return contentToSearch.length() < MIN_LENGTH_TO_COMPARE;
    }

    public boolean isReblogged() {
        return !rebloggers.isEmpty();
    }

    public MyStringBuilder getDetails(Context context, boolean showReceivedTime) {
        MyStringBuilder builder = getMyStringBuilderWithTime(context, showReceivedTime);
        if (isSensitive() && MyPreferences.isShowSensitiveContent()) {
            builder.prependWithSeparator(myContext.context().getText(R.string.sensitive), " ");
        }
        setAudience(builder);
        setNoteSource(context, builder);
        setAccountDownloaded(builder);
        setNoteStatus(context, builder);
        setCollapsedStatus(builder);
        if (detailsSuffix.length() > 0) builder.withSpace(detailsSuffix.toString());
        return builder;
    }

    private void setAudience(MyStringBuilder builder) {
        builder.withSpace(audience.toAudienceString(inReplyToActor.getActor()));
    }

    private void setNoteSource(Context context, MyStringBuilder noteDetails) {
        if (!SharedPreferencesUtil.isEmpty(noteSource) && !"ostatus".equals(noteSource)
                && !"unknown".equals(noteSource)) {
            noteDetails.withSpace(StringUtil.format(context, R.string.message_source_from, noteSource));
        }
    }

    private void setAccountDownloaded(MyStringBuilder noteDetails) {
        if (MyPreferences.isShowMyAccountWhichDownloadedActivity() && linkedMyAccount.isValid()) {
            noteDetails.withSpace("a:" + linkedMyAccount.getShortestUniqueAccountName());
        }
    }

    private void setNoteStatus(Context context, MyStringBuilder noteDetails) {
        if (noteStatus != DownloadStatus.LOADED) {
            noteDetails.withSpace("(").append(noteStatus.getTitle(context)).append(")");
        }
    }

    public BaseNoteViewItem setName(String name) {
        this.nameString = name;
        return this;
    }

    public Spannable getName() {
        return name;
    }

    public BaseNoteViewItem setSummary(String summary) {
        this.summaryString = summary;
        return this;
    }

    public Spannable getSummary() {
        return summary;
    }

    public boolean isSensitive() {
        return isSensitive;
    }

    public BaseNoteViewItem setContent(String content) {
        contentString = content;
        return this;
    }

    public Spannable getContent() {
        return content;
    }

    @Override
    public long getId() {
        return getNoteId();
    }

    @Override
    public long getDate() {
        return activityUpdatedDate;
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        if (filter.keywordsFilter.nonEmpty() || filter.searchQuery.nonEmpty()) {
            if (filter.keywordsFilter.matchedAny(contentToSearch)) return false;

            if (filter.searchQuery.nonEmpty() && !filter.searchQuery.matchedAll(contentToSearch)) return false;
        }
        return !filter.hideRepliesNotToMeOrFriends
                || inReplyToActor.isEmpty()
                || MyContextHolder.get().users().isMeOrMyFriend(inReplyToActor.getActor());
    }

    @Override
    public String toString() {
        return "Note " + content;
    }

    public void hideTheReblogger(Actor actor) {
        rebloggers.remove(actor.actorId);
    }

    @Override
    public void addActorsToLoad(ActorListLoader loader) {
        loader.addActorToList(author.getActor());
        loader.addActorToList(inReplyToActor.getActor());
        audience.addActorsToLoad(loader::addActorToList);
    }

    @Override
    public void setLoadedActors(ActorListLoader loader) {
        if (author.getActor().nonEmpty()) author = loader.getLoaded(author);
        if (inReplyToActor.getActor().nonEmpty()) inReplyToActor = loader.getLoaded(inReplyToActor);
        audience.setLoadedActors((Actor actor) -> loader.getLoaded(ActorViewItem.fromActor(actor)).getActor());
        name = SpanUtil.textToSpannable(nameString, TextMediaType.PLAIN, audience);
        summary = SpanUtil.textToSpannable(summaryString, TextMediaType.PLAIN, audience);
        content = SpanUtil.textToSpannable(contentString, TextMediaType.HTML, audience);
    }
}
