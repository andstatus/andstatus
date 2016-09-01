/**
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
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.CommandTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineTitle;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.ContentValuesUtils;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

import java.util.Queue;

/**
 * Command data store
 * 
 * @author yvolk@yurivolkov.com
 */
public class CommandData implements Comparable<CommandData> {
    private final long commandId;
    private final CommandEnum command;
    private final long createdDate;
    private String description = "";

    private volatile boolean mInForeground = false;
    private volatile boolean mManuallyLaunched = false;

    /** {@link MyAccount} for this command. Invalid account if command is not Account
     * specific e.g. {@link CommandEnum#DELETE_COMMAND}
     * It holds UserId for command, which need such parameter (not only for a timeline)
     */
    private final Timeline timeline;
    /** This is: 1. Generally: Message ID ({@link MsgTable#MSG_ID} of the {@link MsgTable})...
     */
    protected long itemId = 0;
    /** Sometimes we don't know {@link #timeline#getUserId} yet... */
    private String userName = "";

    private volatile int result = 0;
    private CommandResult commandResult = new CommandResult();

    public static CommandData newSearch(MyContext myContext, Origin origin, String queryString) {
        Timeline timeline =  Timeline.getTimeline(myContext, 0, TimelineType.SEARCH, null, 0, origin, queryString);
        CommandData commandData = new CommandData(0, CommandEnum.FETCH_TIMELINE, timeline, 0);
        return commandData;
    }

    public static CommandData newUpdateStatus(MyAccount myAccount, long unsentMessageId) {
        CommandData commandData = newAccountCommand(CommandEnum.UPDATE_STATUS, myAccount);
        commandData.itemId = unsentMessageId;
        commandData.setTrimmedMessageBodyAsDescription(unsentMessageId);
        return commandData;
    }

    public static CommandData newFetchAttachment(long msgId, long downloadDataRowId) {
        CommandData commandData = newAccountCommand(CommandEnum.FETCH_ATTACHMENT, null);
        commandData.itemId = downloadDataRowId;
        commandData.setTrimmedMessageBodyAsDescription(msgId);
        return commandData;
    }

    private void setTrimmedMessageBodyAsDescription(long msgId) {
        if (msgId != 0) {
            description = trimConditionally(
                            MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId), true)
                            .toString();
        }
    }

    @NonNull
    public static CommandData newUserCommand(CommandEnum command, @NonNull Origin origin, long userId, String userName) {
        CommandData commandData = newTimelineCommand(command, null, TimelineType.UNKNOWN, userId, origin);
        commandData.setUserName(userName);
        commandData.description = commandData.getUserName();
        return commandData;
    }

    public static CommandData getEmpty() {
        return newCommand(CommandEnum.EMPTY);
    }

    public static CommandData newCommand(CommandEnum command) {
        return newAccountCommand(command, null);
    }

    public static CommandData newItemCommand(CommandEnum command, MyAccount myAccount, long itemId) {
        CommandData commandData = newAccountCommand(command, myAccount);
        commandData.itemId = itemId;
        return commandData;
    }

    public static CommandData newAccountCommand(CommandEnum command, MyAccount myAccount) {
        return newTimelineCommand(command, Timeline.getTimeline(TimelineType.HOME, myAccount, 0, null));
    }

    public static CommandData newTimelineCommand(CommandEnum command, MyAccount myAccount,
                                                 TimelineType timelineType) {
        return newTimelineCommand(command, Timeline.getTimeline(timelineType, myAccount, 0, null));
    }

    public static CommandData newTimelineCommand(CommandEnum command, MyAccount myAccount,
                                                 TimelineType timelineType, long userId, Origin origin) {
        return newTimelineCommand(command, Timeline.getTimeline(timelineType, myAccount, userId, origin));
    }

    public static CommandData newTimelineCommand(CommandEnum command, Timeline timeline) {
        return new CommandData(0, command, timeline, 0);
    }

    private CommandData(long commandId, CommandEnum command, Timeline timeline, long createdDate) {
        this.commandId = commandId == 0 ? MyLog.uniqueCurrentTimeMS() : commandId;
        this.command = command;
        this.timeline = timeline;
        this.createdDate = createdDate > 0 ? createdDate : this.commandId;
        resetRetries();
    }

    /**
     * Used to decode command from the Intent upon receiving it
     */
    @NonNull
    public static CommandData fromIntent(MyContext myContext, Intent intent) {
        return intent == null  ? getEmpty() : fromBundle(myContext, intent.getExtras());
    }

    public static CommandData fromBundle(MyContext myContext, Bundle bundle) {
        CommandData commandData;
        CommandEnum command = CommandEnum.fromBundle(bundle);
        switch (command) {
            case UNKNOWN:
            case EMPTY:
                commandData = getEmpty();
                break;
            default:
                commandData = new CommandData(
                        bundle.getLong(IntentExtra.COMMAND_ID.key, 0),
                        command,
                        Timeline.fromBundle(myContext, bundle),
                        bundle.getLong(IntentExtra.CREATED_DATE.key));
                commandData.itemId = bundle.getLong(IntentExtra.ITEM_ID.key);
                commandData.setUserName(BundleUtils.getString(bundle, IntentExtra.USER_NAME));
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
        if (intent == null) {
            throw new IllegalArgumentException("toIntent: input intent is null");
        }
        intent.putExtras(toBundle());
        return intent;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        BundleUtils.putNotZero(bundle, IntentExtra.COMMAND_ID, commandId);
        BundleUtils.putNotEmpty(bundle, IntentExtra.COMMAND, command.save());
        timeline.toBundle(bundle);
        BundleUtils.putNotZero(bundle, IntentExtra.ITEM_ID, itemId);
        BundleUtils.putNotEmpty(bundle, IntentExtra.USER_NAME, userName);
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
        ContentValuesUtils.putNotEmpty(values, CommandTable.DESCRIPTION, description);
        values.put(CommandTable.IN_FOREGROUND, mInForeground);
        values.put(CommandTable.MANUALLY_LAUNCHED, mManuallyLaunched);
        timeline.toCommandContentValues(values);
        ContentValuesUtils.putNotZero(values, CommandTable.ITEM_ID, itemId);
        values.put(CommandTable.USERNAME, userName);
        commandResult.toContentValues(values);
    }

    public static CommandData fromCursor(MyContext myContext, Cursor cursor) {
        CommandEnum command = CommandEnum.load(DbUtils.getString(cursor, CommandTable.COMMAND_CODE));
        if (CommandEnum.UNKNOWN.equals(command)) {
            return CommandData.getEmpty();
        }
        CommandData commandData = new CommandData(
                DbUtils.getLong(cursor, CommandTable._ID),
                command,
                Timeline.fromCommandCursor(myContext, cursor),
                DbUtils.getLong(cursor, CommandTable.CREATED_DATE));
        commandData.description = DbUtils.getString(cursor, CommandTable.DESCRIPTION);
        commandData.mInForeground = DbUtils.getBoolean(cursor, CommandTable.IN_FOREGROUND);
        commandData.mManuallyLaunched = DbUtils.getBoolean(cursor, CommandTable.MANUALLY_LAUNCHED);
        commandData.itemId = DbUtils.getLong(cursor, CommandTable.ITEM_ID);
        commandData.setUserName(DbUtils.getString(cursor, CommandTable.USERNAME));
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
            result += prime * timeline.hashCode();
            if (itemId != 0) {
                result += prime * itemId;
            }
            if (!TextUtils.isEmpty(description)) {
                result += prime * description.hashCode();
            }
        }
        return result;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("command:" + command.save() + ",");
        if (mInForeground) {
            builder.append("foreground,");
        }
        if (mManuallyLaunched) {
            builder.append("manual,");
        }
        builder.append("created:"
                + RelativeTime.getDifference(MyContextHolder.get().context(), getCreatedDate())
                + ",");
        if (!TextUtils.isEmpty(description)) {
            builder.append("\"");
            builder.append(description);
            builder.append("\",");
        }
        builder.append(getTimeline().toString() + ",");
        if (itemId != 0) {
            builder.append("itemId:" + itemId + ",");
        }
        if (!TextUtils.isEmpty(userName)) {
            builder.append(", userName:'" + userName + "'");
        }
        builder.append("hashCode:" + hashCode() + ",");
        builder.append(CommandResult.toString(commandResult));
        return MyLog.formatKeyValue("CommandData", builder);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof CommandData)) {
            return false;
        }
        CommandData other = (CommandData) o;
        if (!command.equals(other.command)) {
            return false;
        }
        if (!timeline.equals(other.timeline)) {
            return false;
        }
        if (!description.contentEquals(other.description)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(@NonNull CommandData another) {
        if (another == null) {
            return 0;
        }
        int greater;
        if (this.commandId == another.commandId) {
            return 0;
        } else if (another.command.getPriority() == this.command.getPriority()) {
            greater = this.commandId > another.commandId ? 1 : -1;
        } else {
            greater = this.command.getPriority() > another.command.getPriority() ? 1 : -1;
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
        StringBuilder builder = new StringBuilder(command == CommandEnum.FETCH_TIMELINE ? "" :
                toShortCommandName(myContext));
        if (!summaryOnly) {
            if (mInForeground) {
                I18n.appendWithSpace(builder, ", foreground");
            }
            if (mManuallyLaunched) {
                I18n.appendWithSpace(builder, ", manual");
            }
        }
        switch (command) {
            case FETCH_AVATAR:
                I18n.appendWithSpace(builder, 
                        myContext.context().getText(R.string.combined_timeline_off_account));
                I18n.appendWithSpace(builder, MyQuery.userIdToWebfingerId(timeline.getUserId()));
                if (myContext.persistentAccounts().getDistinctOriginsCount() > 1) {
                    long originId = MyQuery.userIdToLongColumnValue(UserTable.ORIGIN_ID,
                            timeline.getUserId());
                    I18n.appendWithSpace(builder, 
                            myContext.context().getText(R.string.combined_timeline_off_origin));
                    I18n.appendWithSpace(builder, 
                            myContext.persistentOrigins().fromId(originId).getName());
                }
                break;
            case FETCH_ATTACHMENT:
            case UPDATE_STATUS:
                I18n.appendWithSpace(builder, "\"");
                builder.append(trimConditionally(description, summaryOnly));
                builder.append("\"");
                break;
            case FETCH_TIMELINE:
                builder.append(TimelineTitle.load(myContext, timeline, null).toString());
                break;
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
            case GET_FOLLOWERS:
            case GET_FRIENDS:
                I18n.appendWithSpace(builder, MyQuery.userIdToWebfingerId(timeline.getUserId()));
                break;
            case GET_USER:
                if (!TextUtils.isEmpty(getUserName())) {
                    builder.append(" \"");
                    builder.append(getUserName());
                    builder.append("\"");
                }
                break;
            default:
                appendAccountName(myContext, builder);
                break;
        }
        if (!summaryOnly) {            
            builder.append("\n" + createdDateWithLabel(myContext.context())); 
            builder.append("\n" + getResult().toSummary());
        }
        return builder.toString();
    }

    private void appendAccountName(MyContext myContext, StringBuilder builder) {
        if (getTimeline().getMyAccount().isValid()) {
            I18n.appendWithSpace(builder, 
                    getTimelineType().getPrepositionForNotCombinedTimeline(myContext.context()));
            if (getTimelineType().isAtOrigin()) {
                I18n.appendWithSpace(builder, getTimeline().getOrigin().getName());
            } else {
                I18n.appendWithSpace(builder, getTimeline().getMyAccount().getAccountName());
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
        if ( TextUtils.isEmpty(text)) {
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
            case FETCH_TIMELINE:
                builder.append(getTimelineType().getTitle(myContext.context()));
                break;
            default:
                builder.append(command.getTitle(myContext, getTimeline().getMyAccount().getAccountName()));
                break;
        }
        return builder.toString();
    }

    void deleteCommandInTheQueue(Queue<CommandData> queue) {
        String method = "deleteCommandInTheQueue: ";
        for (CommandData cd : queue) {
            if (cd.getCommandId() == itemId) {
                queue.remove(cd);
                getResult().incrementDownloadedCount();
                MyLog.v(this, method + "deleted: " + cd);
            }
        }
        MyLog.v(this, method + "id=" + itemId + ", processed queue: " + queue.size());
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

    public long getUserId() {
        return timeline.getUserId();
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        if (TextUtils.isEmpty(this.userName) && !TextUtils.isEmpty(userName)) {
            this.userName = userName;
        }
    }
}
