/*
 * Copyright (C) 2010-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.ContentValuesUtils;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ListScope;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineTitle;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;

import java.util.Objects;
import java.util.Queue;

/**
 * Command data store
 * 
 * @author yvolk@yurivolkov.com
 */
public class CommandData implements Comparable<CommandData> {
    public final static CommandData EMPTY = newCommand(CommandEnum.EMPTY);
    private final long commandId;
    private final CommandEnum command;
    public final MyAccount myAccount;
    private final long createdDate;
    private String description = "";

    private volatile boolean mInForeground = false;
    private volatile boolean mManuallyLaunched = false;

    /** {@link MyAccount} for this command. Invalid account if command is not Account
     * specific e.g. {@link CommandEnum#DELETE_COMMAND}
     * It holds actorId for command, which need such parameter (not only for a timeline)
     */
    private final Timeline timeline;
    /** This is: 1. Generally: Note ID ({@link NoteTable#NOTE_ID} of the {@link NoteTable})...
     */
    protected long itemId = 0;
    /** Sometimes we don't know {@link Timeline#actor} yet...
     * Used for Actor search also */
    private String username = "";

    private volatile int result = 0;
    private CommandResult commandResult = new CommandResult();

    public static CommandData newSearch(SearchObjects searchObjects,
                                        MyContext myContext, Origin origin, String queryString) {
        if (searchObjects == SearchObjects.NOTES) {
            Timeline timeline =  myContext.timelines().get(TimelineType.SEARCH, 0, origin, queryString);
            return new CommandData(0, CommandEnum.GET_TIMELINE, MyAccount.EMPTY, timeline, 0);
        } else {
            return newActorCommand(CommandEnum.SEARCH_ACTORS, 0, queryString);
        }
    }

    public static CommandData newUpdateStatus(MyAccount myAccount, long unsentActivityId, long noteId) {
        CommandData commandData = newAccountCommand(CommandEnum.UPDATE_NOTE, myAccount);
        commandData.itemId = unsentActivityId;
        commandData.setTrimmedNoteContentAsDescription(noteId);
        return commandData;
    }

    public static CommandData newFetchAttachment(long noteId, long downloadDataRowId) {
        CommandData commandData = newOriginCommand(CommandEnum.GET_ATTACHMENT, Origin.EMPTY);
        commandData.itemId = downloadDataRowId;
        commandData.setTrimmedNoteContentAsDescription(noteId);
        return commandData;
    }

    private void setTrimmedNoteContentAsDescription(long noteId) {
        if (noteId != 0) {
            description = trimConditionally(
                            MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId), true)
                            .toString();
        }
    }

    @NonNull
    public static CommandData newActorCommand(CommandEnum command, long actorId, String username) {
        CommandData commandData = newTimelineCommand(command,
                Timeline.getTimeline(TimelineType.SENT, actorId, Origin.EMPTY));
        commandData.setUsername(username);
        commandData.description = commandData.getUsername();
        return commandData;
    }

    public static CommandData newCommand(CommandEnum command) {
        return newOriginCommand(command, Origin.EMPTY);
    }

    public static CommandData newItemCommand(CommandEnum command, @NonNull MyAccount myAccount, long itemId) {
        CommandData commandData = newAccountCommand(command, myAccount);
        commandData.itemId = itemId;
        return commandData;
    }

    public static CommandData newAccountCommand(CommandEnum command, @NonNull MyAccount myAccount) {
        return new CommandData(0, command, myAccount,
                myAccount.isValid()
                        ? Timeline.getTimeline(TimelineType.OUTBOX, myAccount.getActorId(), Origin.EMPTY)
                        : Timeline.EMPTY,
                0);
    }

    public static CommandData actOnActorCommand(CommandEnum command, MyAccount myAccount, long actorId, String username) {
        if (!myAccount.isValid() || (actorId == 0 && StringUtils.isEmpty(username))) return CommandData.EMPTY;

        CommandData commandData = new CommandData(0, command, myAccount, actorId == 0
                ? Timeline.EMPTY :
                Timeline.getTimeline(TimelineType.SENT, actorId, Origin.EMPTY),
                0);
        commandData.setUsername(username);
        commandData.description = commandData.getUsername();
        return commandData;
    }

    public static CommandData newOriginCommand(CommandEnum command, @NonNull Origin origin) {
        return newTimelineCommand(command, origin.isEmpty()
                ? Timeline.EMPTY
                : Timeline.getTimeline(TimelineType.EVERYTHING, 0, origin));
    }

    public static CommandData newTimelineCommand(CommandEnum command, @NonNull MyAccount myAccount,
                                                 TimelineType timelineType) {
        return newTimelineCommand(command, Timeline.getTimeline(timelineType, myAccount.getActorId(), Origin.EMPTY));
    }

    public static CommandData newTimelineCommand(CommandEnum command, Timeline timeline) {
        return new CommandData(0, command, timeline.myAccountToSync, timeline, 0);
    }

    private CommandData(long commandId, CommandEnum command, MyAccount myAccount, Timeline timeline, long createdDate) {
        this.commandId = commandId == 0 ? MyLog.uniqueCurrentTimeMS() : commandId;
        this.command = command;
        this.myAccount = myAccount;
        this.timeline = timeline;
        this.createdDate = createdDate > 0 ? createdDate : this.commandId;
        resetRetries();
    }

    /**
     * Used to decode command from the Intent upon receiving it
     */
    @NonNull
    public static CommandData fromIntent(MyContext myContext, Intent intent) {
        return intent == null  ? EMPTY : fromBundle(myContext, intent.getExtras());
    }

    public static CommandData fromBundle(MyContext myContext, Bundle bundle) {
        CommandData commandData;
        CommandEnum command = CommandEnum.fromBundle(bundle);
        switch (command) {
            case UNKNOWN:
            case EMPTY:
                commandData = EMPTY;
                break;
            default:
                commandData = new CommandData(
                        bundle.getLong(IntentExtra.COMMAND_ID.key, 0),
                        command,
                        MyAccount.fromBundle(myContext, bundle),
                        Timeline.fromBundle(myContext, bundle),
                        bundle.getLong(IntentExtra.CREATED_DATE.key));
                commandData.itemId = bundle.getLong(IntentExtra.ITEM_ID.key);
                commandData.setUsername(BundleUtils.getString(bundle, IntentExtra.USERNAME));
                commandData.description = BundleUtils.getString(bundle, IntentExtra.COMMAND_DESCRIPTION);
                commandData.mInForeground = bundle.getBoolean(IntentExtra.IN_FOREGROUND.key);
                commandData.mManuallyLaunched = bundle.getBoolean(IntentExtra.MANUALLY_LAUNCHED.key);
                commandData.commandResult = bundle.getParcelable(IntentExtra.COMMAND_RESULT.key);
                break;
        }
        return commandData;
    }

    /**
     * @return Intent to be sent to MyService
     */
    public Intent toIntent(Intent intent) {
        Objects.requireNonNull(intent);
        intent.putExtras(toBundle());
        return intent;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        BundleUtils.putNotEmpty(bundle, IntentExtra.COMMAND, command.save());
        if (command == CommandEnum.EMPTY) return bundle;

        BundleUtils.putNotZero(bundle, IntentExtra.COMMAND_ID, commandId);
        if (myAccount.isValid()) {
            bundle.putString(IntentExtra.ACCOUNT_NAME.key, myAccount.getAccountName());
        }
        timeline.toBundle(bundle);
        BundleUtils.putNotZero(bundle, IntentExtra.ITEM_ID, itemId);
        BundleUtils.putNotEmpty(bundle, IntentExtra.USERNAME, username);
        BundleUtils.putNotEmpty(bundle, IntentExtra.COMMAND_DESCRIPTION, description);
        bundle.putBoolean(IntentExtra.IN_FOREGROUND.key, mInForeground);
        bundle.putBoolean(IntentExtra.MANUALLY_LAUNCHED.key, mManuallyLaunched);
        bundle.putParcelable(IntentExtra.COMMAND_RESULT.key, commandResult);
        return bundle;
    }

    public void toContentValues(ContentValues values) {
        ContentValuesUtils.putNotZero(values, CommandTable._ID, commandId);
        ContentValuesUtils.putNotZero(values, CommandTable.CREATED_DATE, createdDate);
        values.put(CommandTable.COMMAND_CODE, command.save());
        values.put(CommandTable.ACCOUNT_ID, myAccount.getActorId());
        ContentValuesUtils.putNotEmpty(values, CommandTable.DESCRIPTION, description);
        values.put(CommandTable.IN_FOREGROUND, mInForeground);
        values.put(CommandTable.MANUALLY_LAUNCHED, mManuallyLaunched);
        timeline.toCommandContentValues(values);
        ContentValuesUtils.putNotZero(values, CommandTable.ITEM_ID, itemId);
        values.put(CommandTable.USERNAME, username);
        commandResult.toContentValues(values);
    }

    public static CommandData fromCursor(MyContext myContext, Cursor cursor) {
        CommandEnum command = CommandEnum.load(DbUtils.getString(cursor, CommandTable.COMMAND_CODE));
        if (CommandEnum.UNKNOWN.equals(command)) return CommandData.EMPTY;

        CommandData commandData = new CommandData(
                DbUtils.getLong(cursor, CommandTable._ID),
                command,
                myContext.accounts().fromActorId(DbUtils.getLong(cursor, CommandTable.ACCOUNT_ID)),
                Timeline.fromCommandCursor(myContext, cursor),
                DbUtils.getLong(cursor, CommandTable.CREATED_DATE));
        commandData.description = DbUtils.getString(cursor, CommandTable.DESCRIPTION);
        commandData.mInForeground = DbUtils.getBoolean(cursor, CommandTable.IN_FOREGROUND);
        commandData.mManuallyLaunched = DbUtils.getBoolean(cursor, CommandTable.MANUALLY_LAUNCHED);
        commandData.itemId = DbUtils.getLong(cursor, CommandTable.ITEM_ID);
        commandData.setUsername(DbUtils.getString(cursor, CommandTable.USERNAME));
        commandData.commandResult = CommandResult.fromCursor(cursor);
        return commandData;
    }

    public String getSearchQuery() {
        return timeline.getSearchQuery();
    }

    /**
     * It's used in equals() method. We need to distinguish duplicated commands but to ignore
     * differences in results!
     */
    @Override
    public int hashCode() {
        if (result == 0) {
            final int prime = 31;
            result = 1;
            result += command.save().hashCode();
            result = 31 * result + myAccount.hashCode();
            result += prime * timeline.hashCode();
            if (itemId != 0) result += prime * itemId;
            if (!StringUtils.isEmpty(description)) result += prime * description.hashCode();
        }
        return result;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        if (this == EMPTY) return MyLog.formatKeyValue(this, "EMPTY");
        StringBuilder builder = new StringBuilder();
        builder.append("command:" + command.save());
        if (mInForeground) {
            builder.append(",foreground");
        }
        if (mManuallyLaunched) {
            builder.append(",manual");
        }
        builder.append(",created:" + RelativeTime.getDifference(MyContextHolder.get().context(), getCreatedDate()));
        if (StringUtils.nonEmpty(username)) {
            builder.append(",actor:'" + username + "'");
        }
        if (StringUtils.nonEmpty(description) && !description.equals(username)) {
            builder.append(",\"");
            builder.append(description);
            builder.append("\"");
        }
        if (getTimeline().isValid()) {
            builder.append("," + getTimeline().toString());
        }
        if (itemId != 0) {
            builder.append(",itemId:" + itemId);
        }
        builder.append(",hashCode:" + hashCode());
        builder.append("," + CommandResult.toString(commandResult));
        return MyLog.formatKeyValue(this, builder);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof CommandData)) return false;
        CommandData other = (CommandData) o;
        if (!command.equals(other.command)) return false;
        if (!myAccount.equals(other.myAccount)) return false;
        if (!timeline.equals(other.timeline)) return false;
        if (itemId != other.itemId) return false;
        return StringUtils.equalsNotEmpty(description, other.description);
    }

    @Override
    public int compareTo(@NonNull CommandData another) {
        int greater;
        if (isInForeground() != another.isInForeground()) {
            return isInForeground() ? -1 : 1;
        }
        if (commandId == another.commandId) {
            return 0;
        } else if (command.getPriority() == another.command.getPriority()) {
            greater = commandId > another.commandId ? 1 : -1;
        } else {
            greater = command.getPriority() > another.command.getPriority() ? 1 : -1;
        }
        return greater;
    }

    public CommandEnum getCommand() {
        return command;
    }

    public TimelineType getTimelineType() {
        return timeline.getTimelineType();
    }

    public boolean isManuallyLaunched() {
        return mManuallyLaunched;
    }

    public CommandData setManuallyLaunched(boolean manuallyLaunched) {
        this.mManuallyLaunched = manuallyLaunched;
        return this;
    }

    public CommandResult getResult() {
        return commandResult;
    }

    public String share(MyContext myContext) {
        return toUserFriendlyForm(myContext, false);
    }

    public String toCommandSummary(MyContext myContext) {
        return toUserFriendlyForm(myContext, true);
    }

    private String toUserFriendlyForm(MyContext myContext, boolean summaryOnly) {
        StringBuilder builder = new StringBuilder(
                command == CommandEnum.GET_TIMELINE || command == CommandEnum.GET_OLDER_TIMELINE ? "" :
                toShortCommandName(myContext));
        if (!summaryOnly) {
            if (mInForeground) {
                MyStringBuilder.appendWithSpace(builder, ", foreground");
            }
            if (mManuallyLaunched) {
                MyStringBuilder.appendWithSpace(builder, ", manual");
            }
        }
        switch (command) {
            case GET_AVATAR:
                MyStringBuilder.appendWithSpace(builder,
                        MyStringBuilder.appendWithSpace(builder, ListScope.ORIGIN.timelinePreposition(myContext)));
                MyStringBuilder.appendWithSpace(builder, MyQuery.actorIdToWebfingerId(timeline.getActorId()));
                if (myContext.accounts().getDistinctOriginsCount() > 1) {
                    long originId = MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID,
                            timeline.getActorId());
                    MyStringBuilder.appendWithSpace(builder, ListScope.ORIGIN.timelinePreposition(myContext));
                    MyStringBuilder.appendWithSpace(builder,
                            myContext.origins().fromId(originId).getName());
                }
                break;
            case GET_ATTACHMENT:
            case UPDATE_NOTE:
                MyStringBuilder.appendWithSpace(builder, "\"");
                builder.append(trimConditionally(description, summaryOnly));
                builder.append("\"");
                break;
            case GET_TIMELINE:
                builder.append(TimelineTitle.load(myContext, timeline, myContext.accounts().getCurrentAccount()).title);
                break;
            case GET_OLDER_TIMELINE:
                builder.append(WhichPage.OLDER.getTitle(myContext.context()));
                builder.append(" ");
                builder.append(TimelineTitle.load(myContext, timeline, myContext.accounts().getCurrentAccount()).title);
                break;
            case FOLLOW:
            case UNDO_FOLLOW:
            case GET_FOLLOWERS:
            case GET_FRIENDS:
                MyStringBuilder.appendWithSpace(builder, timeline.actor.getTimelineUsername());
                break;
            case GET_ACTOR:
            case SEARCH_ACTORS:
                if (StringUtils.nonEmpty(getUsername())) {
                    builder.append(" \"");
                    builder.append(getUsername());
                    builder.append("\"");
                }
                break;
            default:
                appendScopeName(myContext, builder);
                break;
        }
        if (!summaryOnly) {            
            builder.append("\n" + createdDateWithLabel(myContext.context())); 
            builder.append("\n" + getResult().toSummary());
        }
        return builder.toString();
    }

    private void appendScopeName(MyContext myContext, StringBuilder builder) {
        if (getTimeline().myAccountToSync.isValid()) {
            MyStringBuilder.appendWithSpace(builder,
                    getTimelineType().scope.timelinePreposition(myContext));
            if (getTimelineType().isAtOrigin()) {
                MyStringBuilder.appendWithSpace(builder, getTimeline().getOrigin().getName());
            } else {
                MyStringBuilder.appendWithSpace(builder, getTimeline().myAccountToSync.getAccountName());
            }
        }
    }

    public String createdDateWithLabel(Context context) {
        return context.getText(R.string.created_label)
                       + " "
                       + RelativeTime.getDifference(context, getCreatedDate());
    }

    @NonNull
    private static CharSequence trimConditionally(String text, boolean trim) {
        if ( StringUtils.isEmpty(text)) {
            return "";
        } else if (trim) {
            return I18n.trimTextAt(MyHtml.fromHtml(text), 40);
        } else {
            return text;
        }
    }

    public String toCommandProgress(MyContext myContext) {
        return toShortCommandName(myContext) + "; " + getResult().getProgress();
    }

    public String toShortCommandName(MyContext myContext) {
        StringBuilder builder = new StringBuilder();
        switch (command) {
            case GET_TIMELINE:
                builder.append(getTimelineType().title(myContext.context()));
                break;
            case GET_OLDER_TIMELINE:
                builder.append(WhichPage.OLDER.getTitle(myContext.context()));
                builder.append(" ");
                builder.append(getTimelineType().title(myContext.context()));
                break;
            default:
                builder.append(command.getTitle(myContext, getTimeline().myAccountToSync.getAccountName()));
                break;
        }
        return builder.toString();
    }

    void deleteCommandFromQueue(Queue<CommandData> queue) {
        String method = "deleteCommandFromQueue: ";
        for (CommandData cd : queue) {
            if (cd.getCommandId() == itemId) {
                queue.remove(cd);
                getResult().incrementDownloadedCount();
                MyLog.v(this, () -> method + "deleted: " + cd);
            }
        }
        MyLog.v(this, () -> method + "id=" + itemId + ", processed queue: " + queue.size());
    }

    public boolean isInForeground() {
        return mInForeground;
    }

    public CommandData setInForeground(boolean inForeground) {
        mInForeground = inForeground;
        return this;
    }

    public boolean executedMoreSecondsAgoThan(long predefinedPeriodSeconds) {
        return RelativeTime.moreSecondsAgoThan(getResult().getLastExecutedDate(),
                predefinedPeriodSeconds);
    }

    public final void resetRetries() {
        getResult().resetRetries(getCommand());
    }

    public long getCommandId() {
        return commandId;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public Timeline getTimeline() {
        return timeline;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (StringUtils.isEmpty(this.username) && !StringUtils.isEmpty(username)) {
            this.username = username;
        }
    }
}
