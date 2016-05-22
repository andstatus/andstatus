/**
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.database.CommandTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.msg.TimelineListParameters;
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
    private final CommandEnum command;
    private final long id;
    private final long createdDate;

    /**
     * Unique name of {@link MyAccount} for this command. Empty string if command is not Account
     * specific (e.g. {@link CommandEnum#AUTOMATIC_UPDATE} which works for all accounts)
     */
    private final String accountName;

    /**
     * Timeline type used for the {@link CommandEnum#FETCH_TIMELINE} command
     */
    private final TimelineType timelineType;

    private volatile boolean mInForeground = false;
    private volatile boolean mManuallyLaunched = false;

    /**
     * This is: 1. Generally: Message ID ({@link MsgTable#MSG_ID} of the
     * {@link MsgTable}). 2. User ID ( {@link UserTable#USER_ID} ) for the
     * {@link CommandEnum#FETCH_TIMELINE}, {@link CommandEnum#FOLLOW_USER},
     * {@link CommandEnum#STOP_FOLLOWING_USER}
     */
    protected long itemId = 0;

    /**
     * Other command parameters
     */
    Bundle bundle = new Bundle();

    private volatile int result = 0;

    private CommandResult commandResult = new CommandResult();

    public static CommandData searchCommand(String accountName, String queryString) {
        CommandData commandData = new CommandData(CommandEnum.SEARCH_MESSAGE, accountName,
                TimelineType.PUBLIC);
        commandData.bundle.putString(IntentExtra.SEARCH_QUERY.key, queryString);
        return commandData;
    }

    public static CommandData updateStatus(String accountName, long unsentMessageId) {
        CommandData commandData = new CommandData(CommandEnum.UPDATE_STATUS, accountName, unsentMessageId);
        putTrimmedMessageBody(commandData, unsentMessageId);
        return commandData;
    }

    public static CommandData getUser(String accountName, long userId, String userName) {
        CommandData commandData = new CommandData(CommandEnum.GET_USER, accountName, userId);
        commandData.bundle.putString(IntentExtra.USER_NAME.key, userName);
        return commandData;
    }

    public static CommandData forOneExecStep(CommandExecutionContext execContext) {
        CommandData commandData = CommandData.fromIntent(
                execContext.getCommandData().toIntent(new Intent()),
                execContext.getMyAccount().getAccountName(),
                execContext.getTimelineType()
                );
        commandData.commandResult = execContext.getCommandData().getResult().forOneExecStep();
        return commandData;
    }

    /**
     * Used to decode command from the Intent upon receiving it
     */
    public static CommandData fromIntent(Intent intent) {
        return fromIntent(intent, "", TimelineType.UNKNOWN);
    }

    private static CommandData fromIntent(Intent intent, String accountNameIn,
            TimelineType timelineTypeIn) {
        CommandData commandData = getEmpty();
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            String strCommand = "";
            if (bundle != null) {
                strCommand = bundle.getString(IntentExtra.COMMAND.key);
            }
            CommandEnum command = CommandEnum.load(strCommand);
            switch (command) {
                case UNKNOWN:
                    MyLog.w(CommandData.class, "Intent had UNKNOWN command " + strCommand
                            + "; Intent: " + intent);
                    break;
                case EMPTY:
                    break;
                default:
                    String accountName2 = accountNameIn;
                    if (TextUtils.isEmpty(accountName2)) {
                        accountName2 = bundle.getString(IntentExtra.ACCOUNT_NAME.key);
                    }
                    TimelineType timelineType2 = timelineTypeIn;
                    if (timelineType2 == TimelineType.UNKNOWN) {
                        timelineType2 = TimelineType.load(
                                bundle.getString(IntentExtra.TIMELINE_TYPE.key));
                    }
                    commandData = new CommandData(
                            bundle.getLong(IntentExtra.COMMAND_ID.key, 0),
                            command, accountName2, timelineType2,
                            bundle.getLong(IntentExtra.ITEM_ID.key));
                    commandData.bundle = bundle;
                    commandData.mInForeground = commandData.bundle
                            .getBoolean(IntentExtra.IN_FOREGROUND.key);
                    commandData.mManuallyLaunched = commandData.bundle
                            .getBoolean(IntentExtra.MANUALLY_LAUNCHED.key);
                    commandData.commandResult = commandData.bundle
                            .getParcelable(IntentExtra.COMMAND_RESULT.key);
                    break;
            }
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
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putLong(IntentExtra.COMMAND_ID.key, id);
        bundle.putString(IntentExtra.COMMAND.key, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            bundle.putString(IntentExtra.ACCOUNT_NAME.key, getAccountName());
        }
        if (timelineType != TimelineType.UNKNOWN) {
            bundle.putString(IntentExtra.TIMELINE_TYPE.key, timelineType.save());
        }
        if (itemId != 0) {
            bundle.putLong(IntentExtra.ITEM_ID.key, itemId);
        }
        bundle.putBoolean(IntentExtra.IN_FOREGROUND.key, mInForeground);
        bundle.putBoolean(IntentExtra.MANUALLY_LAUNCHED.key, mManuallyLaunched);
        bundle.putParcelable(IntentExtra.COMMAND_RESULT.key, commandResult);
        intent.putExtras(bundle);
        return intent;
    }

    public static CommandData getEmpty() {
        return new CommandData(CommandEnum.EMPTY, "");
    }

    public void accumulateOneStep(CommandData commandData) {
        commandResult.accumulateOneStep(commandData.getResult());
    }

    public void toContentValues(ContentValues values) {
        values.put(CommandTable._ID, id);
        values.put(CommandTable.CREATED_DATE, createdDate);
        values.put(CommandTable.COMMAND_CODE, command.save());
        if (getAccount().isValid()) {
            values.put(CommandTable.ACCOUNT_ID, getAccount().getUserId());
        }
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save());
        values.put(CommandTable.ITEM_ID, itemId);
        values.put(CommandTable.IN_FOREGROUND, mInForeground);
        values.put(CommandTable.MANUALLY_LAUNCHED, mManuallyLaunched);
        switch (command) {
            case FETCH_ATTACHMENT:
            case UPDATE_STATUS:
                values.put(CommandTable.BODY, getMessageText());
                break;
            case SEARCH_MESSAGE:
                values.put(CommandTable.SEARCH_QUERY, getSearchQuery());
                break;
            case GET_USER:
                values.put(CommandTable.USERNAME, getUserName());
                break;
            default:
                break;
        }
        commandResult.toContentValues(values);
    }

    public static CommandData fromCursor(Cursor cursor) {
        CommandEnum command = CommandEnum.load(DbUtils.getString(cursor, CommandTable.COMMAND_CODE));
        if (CommandEnum.UNKNOWN.equals(command)) {
            return CommandData.getEmpty();
        }
        CommandData commandData = new CommandData(
                DbUtils.getLong(cursor, CommandTable._ID),
                command,
                accountIdToName(DbUtils.getLong(cursor, CommandTable.ACCOUNT_ID)),
                TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE)),
                DbUtils.getLong(cursor, CommandTable.ITEM_ID),
                DbUtils.getLong(cursor, CommandTable.CREATED_DATE));
        commandData.bundle.putBoolean(IntentExtra.IN_FOREGROUND.key,
                DbUtils.getBoolean(cursor, CommandTable.IN_FOREGROUND));

        switch (commandData.command) {
            case FETCH_ATTACHMENT:
            case UPDATE_STATUS:
                commandData.bundle.putString(IntentExtra.MESSAGE_TEXT.key,
                        DbUtils.getString(cursor, CommandTable.BODY));
                break;
            case SEARCH_MESSAGE:
                commandData.bundle.putString(IntentExtra.SEARCH_QUERY.key,
                        DbUtils.getString(cursor, CommandTable.SEARCH_QUERY));
                break;
            case GET_USER:
                commandData.bundle.putString(IntentExtra.USER_NAME.key,
                        DbUtils.getString(cursor, CommandTable.USERNAME));
                break;
            default:
                break;
        }
        commandData.commandResult = CommandResult.fromCursor(cursor);
        return commandData;
    }

    @NonNull
    private static String accountIdToName(Long accountId) {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromUserId(accountId);
        if (ma.isValid()) {
            return ma.getAccountName();
        }
        return "";
    }

    public static CommandData fetchAttachment(long msgId, long downloadDataRowId) {
        CommandData commandData = new CommandData(CommandEnum.FETCH_ATTACHMENT, null,
                downloadDataRowId);
        putTrimmedMessageBody(commandData, msgId);
        return commandData;
    }

    private static void putTrimmedMessageBody(CommandData commandData, long msgId) {
        if (msgId != 0) {
            commandData.bundle.putString(
                    IntentExtra.MESSAGE_TEXT.key,
                    trimConditionally(
                            MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId), true)
                            .toString());
        }
    }

    public CommandData(CommandEnum commandIn, String accountNameIn) {
        this(commandIn, accountNameIn, TimelineType.UNKNOWN);
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, long itemIdIn) {
        this(commandIn, accountNameIn, TimelineType.UNKNOWN, itemIdIn);
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, TimelineType timelineTypeIn) {
        this(commandIn, accountNameIn, timelineTypeIn, 0);
    }

    public CommandData(CommandEnum commandIn, String accountNameIn,
            TimelineType timelineTypeIn, long itemIdIn) {
        this(0, commandIn, accountNameIn, timelineTypeIn, itemIdIn);
    }

    private CommandData(long id, CommandEnum commandIn, String accountNameIn,
                        TimelineType timelineTypeIn, long itemIdIn) {
        this(id, commandIn, accountNameIn, timelineTypeIn, itemIdIn, 0);
    }

    private CommandData(long idIn, CommandEnum commandIn, String accountNameIn,
            TimelineType timelineTypeIn, long itemIdIn, long createdDate) {
        if (idIn == 0) {
            id = MyLog.uniqueCurrentTimeMS();
        } else {
            id = idIn;
        }
        if (createdDate > 0) {
            this.createdDate = createdDate;
        } else {
            // This is by implementation
            this.createdDate = id;
        }

        command = commandIn;
        String accountName2 = "";
        TimelineType timelineType2 = TimelineType.UNKNOWN;
        switch (command) {
            case FETCH_ATTACHMENT:
            case FETCH_AVATAR:
                break;
            default:
                if (!TextUtils.isEmpty(accountNameIn)) {
                    accountName2 = accountNameIn;
                }
                timelineType2 = timelineTypeIn;
                break;
        }
        accountName = accountName2;
        timelineType = timelineType2;
        itemId = itemIdIn;
        resetRetries();
    }

    public String getMessageText() {
        return getExtraText(IntentExtra.MESSAGE_TEXT);
    }

    public String getSearchQuery() {
        return getExtraText(IntentExtra.SEARCH_QUERY);
    }

    public String getUserName() {
        return getExtraText(IntentExtra.USER_NAME);
    }

    private String getExtraText(IntentExtra intentExtra) {
        String value = "";
        if (bundle.containsKey(intentExtra.key)) {
            String value1 = bundle.getString(intentExtra.key);
            if (!TextUtils.isEmpty(value1)) {
                value = value1;
            }
        }
        return value;
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
            if (!TextUtils.isEmpty(getAccountName())) {
                result += prime * getAccountName().hashCode();
            }
            if (timelineType != TimelineType.UNKNOWN) {
                result += prime * timelineType.save().hashCode();
            }
            if (itemId != 0) {
                result += prime * itemId;
            }
            if (!TextUtils.isEmpty(getMessageText())) {
                result += prime * getMessageText().hashCode();
            }
            if (!TextUtils.isEmpty(getSearchQuery())) {
                result += prime * getSearchQuery().hashCode();
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
        if (timelineType != TimelineType.UNKNOWN) {
            builder.append(timelineType + ",");
        }
        if (mInForeground) {
            builder.append("foreground,");
        }
        if (mManuallyLaunched) {
            builder.append("manual,");
        }
        builder.append("created:"
                + RelativeTime.getDifference(MyContextHolder.get().context(), getCreatedDate())
                + ",");
        switch (command) {
            case UPDATE_STATUS:
            case FETCH_ATTACHMENT:
                builder.append("\"");
                builder.append(bundle.getString(IntentExtra.MESSAGE_TEXT.key));
                builder.append("\",");
                break;
            case SEARCH_MESSAGE:
                builder.append("\"");
                builder.append(getSearchQuery());
                builder.append("\",");
                break;
            case GET_USER:
                String userName = getUserName();
                if (!TextUtils.isEmpty(userName)) {
                    builder.append("\"");
                    builder.append(userName);
                    builder.append("\",");
                }
                break;
            default:
                break;
        }
        if (!TextUtils.isEmpty(getAccountName())) {
            builder.append("account:" + getAccountName() + ",");
        }
        if (itemId != 0) {
            builder.append("itemId:" + itemId + ",");
        }
        builder.append("hashCode:" + hashCode() + ",");
        builder.append(CommandResult.toString(commandResult));
        return MyLog.formatKeyValue("CommandData", builder);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommandData)) {
            return false;
        }
        CommandData other = (CommandData) o;
        if (hashCode() != other.hashCode()) {
            return false;
        }
        if (!getAccountName().contentEquals(other.getAccountName())) {
            return false;
        }
        if (!getMessageText().contentEquals(other.getMessageText())) {
            return false;
        }
        if (!getSearchQuery().contentEquals(other.getSearchQuery())) {
            return false;
        }
        return true;
    }

    public String getAccountName() {
        return accountName;
    }

    /**
     * @return Invalid account if no MyAccount exists with supplied name
     */
    public MyAccount getAccount() {
        return MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
    }

    @Override
    public int compareTo(@NonNull CommandData another) {
        if (another == null) {
            return 0;
        }
        int greater;
        if (this.id == another.id) {
            return 0;
        } else if (another.command.getPriority() == this.command.getPriority()) {
            greater = this.id > another.id ? 1 : -1;
        } else {
            greater = this.command.getPriority() > another.command.getPriority() ? 1 : -1;
        }
        return greater;
    }

    public CommandEnum getCommand() {
        return command;
    }

    public TimelineType getTimelineType() {
        return timelineType;
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
        StringBuilder builder = new StringBuilder(toShortCommandName(myContext));
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
                I18n.appendWithSpace(builder, MyQuery.userIdToWebfingerId(itemId));
                if (myContext.persistentAccounts().getDistinctOriginsCount() > 1) {
                    long originId = MyQuery.userIdToLongColumnValue(UserTable.ORIGIN_ID,
                            itemId);
                    I18n.appendWithSpace(builder, 
                            myContext.context().getText(R.string.combined_timeline_off_origin));
                    I18n.appendWithSpace(builder, 
                            myContext.persistentOrigins().fromId(originId).getName());
                }
                break;
            case FETCH_ATTACHMENT:
            case UPDATE_STATUS:
                I18n.appendWithSpace(builder, "\"");
                builder.append(trimConditionally(
                        bundle.getString(IntentExtra.MESSAGE_TEXT.key), summaryOnly));
                builder.append("\"");
                break;
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
                if (!TextUtils.isEmpty(accountName)) {
                    if (timelineType == TimelineType.USER) {
                        I18n.appendWithSpace(builder, MyQuery.userIdToWebfingerId(itemId));
                    }
                    I18n.appendWithSpace(builder, 
                            timelineType.getPrepositionForNotCombinedTimeline(myContext
                            .context()));
                    MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
                    if (ma == null) {
                        I18n.appendWithSpace(builder, "('" + accountName + "' ?)");
                    } else {
                        I18n.appendWithSpace(builder,
                                TimelineListParameters.toAccountButtonText(ma.getUserId()));
                    }
                }
                break;
            case FOLLOW_USER:
            case STOP_FOLLOWING_USER:
            case GET_FOLLOWERS:
            case GET_FRIENDS:
                I18n.appendWithSpace(builder, MyQuery.userIdToWebfingerId(itemId));
                break;
            case SEARCH_MESSAGE:
                I18n.appendWithSpace(builder, " \"");
                builder.append(trimConditionally(getSearchQuery(), summaryOnly));
                builder.append("\"");
                appendAccountName(myContext, builder);
                break;
            case GET_USER:
                String userName = getUserName();
                if (!TextUtils.isEmpty(userName)) {
                    builder.append(" \"");
                    builder.append(userName);
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
        if (!TextUtils.isEmpty(accountName)) {
            I18n.appendWithSpace(builder, 
                    timelineType.getPrepositionForNotCombinedTimeline(myContext.context()));
            if (timelineType.isAtOrigin()) {
                MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
                I18n.appendWithSpace(builder, ma != null ? ma.getOrigin().getName() : "?");
            } else {
                I18n.appendWithSpace(builder, accountName);
            }
        }
    }

    public String createdDateWithLabel(Context context) {
        return context.getText(R.string.created_label)
                       + " "
                       + RelativeTime.getDifference(context, getCreatedDate());
    }

    private static CharSequence trimConditionally(String text, boolean trim) {
        if (trim) {
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
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
                builder.append(timelineType.getTitle(myContext.context()));
                break;
            default:
                builder.append(command.getTitle(myContext, accountName));
                break;
        }
        return builder.toString();
    }

    void deleteCommandInTheQueue(Queue<CommandData> queue) {
        String method = "deleteCommandInTheQueue: ";
        for (CommandData cd : queue) {
            if (cd.getId() == itemId) {
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

    public long getId() {
        return id;
    }

    public long getCreatedDate() {
        return createdDate;
    }
}
