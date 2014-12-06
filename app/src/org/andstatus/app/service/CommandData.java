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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.UriUtils;

import java.util.Queue;

/**
 * Command data store (message...)
 * 
 * @author yvolk@yurivolkov.com
 */
public class CommandData implements Comparable<CommandData> {
    private final CommandEnum command;
    private int priority = 0;
    
    /**
     * Unique name of {@link MyAccount} for this command. Empty string if command is not Account specific 
     * (e.g. {@link CommandEnum#AUTOMATIC_UPDATE} which works for all accounts) 
     */
    private final String accountName;

    /**
     * Timeline type used for the {@link CommandEnum#FETCH_TIMELINE} command 
     */
    private final TimelineTypeEnum timelineType;

    private volatile boolean mInForeground = false;
    private volatile boolean mManuallyLaunched = false;

    /**
     * This is: 
     * 1. Generally: Message ID ({@link MyDatabase.Msg#MSG_ID} of the {@link MyDatabase.Msg}).
     * 2. User ID ( {@link MyDatabase.User#USER_ID} ) for the {@link CommandEnum#FETCH_USER_TIMELINE}, 
     *      {@link CommandEnum#FOLLOW_USER}, {@link CommandEnum#STOP_FOLLOWING_USER} 
       */
    protected long itemId = 0;

    /**
     * Other command parameters
     */
    Bundle bundle = new Bundle();

    private volatile int result = 0;

    private CommandResult commandResult = new CommandResult();

    public static CommandData fetchAttachment(long msgId, long downloadDataRowId) {
        CommandData commandData = new CommandData(CommandEnum.FETCH_ATTACHMENT, null, downloadDataRowId);
        if (msgId != 0) {
            commandData.bundle.putString(
                    IntentExtra.EXTRA_MESSAGE_TEXT.key,
                    trimmedTextForSummary(
                            MyProvider.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId))
                            .toString());
        }
        return commandData;
    }
    
    public CommandData(CommandEnum commandIn, String accountNameIn) {
        this(commandIn, accountNameIn, TimelineTypeEnum.UNKNOWN);
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, long itemIdIn) {
        this(commandIn, accountNameIn, TimelineTypeEnum.UNKNOWN, itemIdIn);
    }
	
    public CommandData(CommandEnum commandIn, String accountNameIn, TimelineTypeEnum timelineTypeIn, long itemIdIn) {
        this(commandIn, accountNameIn, timelineTypeIn);
		itemId = itemIdIn;
    }
	
    public Uri getMediaUri() {
        String uriString = getExtraText(IntentExtra.EXTRA_MEDIA_URI);
        if (!TextUtils.isEmpty(uriString)) {
            return Uri.parse(uriString);
        }
        return null;
    }

    public String getMessageText() {
        return getExtraText(IntentExtra.EXTRA_MESSAGE_TEXT);
    }

    public String getSearchQuery() {
        return getExtraText(IntentExtra.EXTRA_SEARCH_QUERY);
    }

    private String getExtraText(IntentExtra intentExtra) {
        String value = "";
        if (bundle.containsKey(intentExtra.key)) {
            String value1 =  bundle.getString(intentExtra.key);
            if (!TextUtils.isEmpty(value1)) {
                value = value1;
            }
        }
        return value;
    }
    
    public CommandData(CommandEnum commandIn, String accountNameIn, TimelineTypeEnum timelineTypeIn) {
        command = commandIn;
		String accountName2 = "";
		TimelineTypeEnum timelineType2 = TimelineTypeEnum.UNKNOWN;
        priority = command.getPriority();
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
        resetRetries();
    }

    public static CommandData forOneExecStep(CommandExecutionContext execContext) {
        CommandData commandData = CommandData.fromIntent(execContext.getCommandData().toIntent(new Intent()),
			execContext.getMyAccount().getAccountName(),
			execContext.getTimelineType()
			);
        commandData.commandResult = execContext.getCommandData().getResult().forOneExecStep();
        return commandData;
    }
    
    public void accumulateOneStep(CommandData commandData) {
        commandResult.accumulateOneStep(commandData.getResult());
    }
    
    /**
     * Used to decode command from the Intent upon receiving it
     */
	public static CommandData fromIntent(Intent intent) {
		return fromIntent(intent, "", TimelineTypeEnum.UNKNOWN);
	}
	
    private static CommandData fromIntent(Intent intent, String accountNameIn, TimelineTypeEnum timelineTypeIn) {
        CommandData commandData = getEmpty();
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            String strCommand = "";
            if (bundle != null) {
                strCommand = bundle.getString(IntentExtra.EXTRA_MSGTYPE.key);
            }
            CommandEnum command = CommandEnum.load(strCommand);
            switch (command) {
                case UNKNOWN:
                    MyLog.w(CommandData.class, "Intent had UNKNOWN command " + strCommand + "; Intent: " + intent);
                    break;
                case EMPTY:
                    break;
                default:
					String accountName2 = accountNameIn;
					if ( TextUtils.isEmpty(accountName2)) {
						accountName2 = bundle.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key);
					}
					TimelineTypeEnum timelineType2 = timelineTypeIn;
					if ( timelineType2 == TimelineTypeEnum.UNKNOWN ) {
						timelineType2 = TimelineTypeEnum.load(
						    bundle.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
					}
                    commandData = new CommandData(command, accountName2, timelineType2);
					commandData.bundle = bundle;
                    commandData.itemId = commandData.bundle.getLong(IntentExtra.EXTRA_ITEMID.key);
                    commandData.mInForeground = commandData.bundle.getBoolean(IntentExtra.EXTRA_IN_FOREGROUND.key);
                    commandData.mManuallyLaunched = commandData.bundle.getBoolean(IntentExtra.EXTRA_MANUALLY_LAUCHED.key);
                    commandData.commandResult = commandData.bundle.getParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key);
                    break;
            }
        }
        return commandData;
    }

    public static CommandData getEmpty() {
        return new CommandData(CommandEnum.EMPTY, "");        
    }
    
    public static CommandData searchCommand(String accountName, String queryString) {
        CommandData commandData = new CommandData(CommandEnum.SEARCH_MESSAGE, accountName, TimelineTypeEnum.PUBLIC);
        commandData.mInForeground = true;
        commandData.mManuallyLaunched = true;
        commandData.bundle.putString(IntentExtra.EXTRA_SEARCH_QUERY.key, queryString);
        return commandData;
    }

    public static CommandData updateStatus(String accountName, String status, long replyToId, long recipientId, Uri mediaUri) {
        CommandData commandData = new CommandData(CommandEnum.UPDATE_STATUS, accountName);
        commandData.mInForeground = true;
        commandData.mManuallyLaunched = true;
        commandData.bundle.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key, status);
        if (replyToId != 0) {
            commandData.bundle.putLong(IntentExtra.EXTRA_INREPLYTOID.key, replyToId);
        }
        if (recipientId != 0) {
            commandData.bundle.putLong(IntentExtra.EXTRA_RECIPIENTID.key, recipientId);
        }
        if (!UriUtils.isEmpty(mediaUri)) {
            commandData.bundle.putString(IntentExtra.EXTRA_MEDIA_URI.key, mediaUri.toString());
        }
        return commandData;
    }
    
    /**
     * Restore this from the SharedPreferences 
     * @param sp
     * @param index Index of the preference's name to be used
     */
    private static CommandData fromSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);
        CommandEnum command = CommandEnum.load(sp.getString(IntentExtra.EXTRA_MSGTYPE.key + si,
                CommandEnum.EMPTY.save()));
		if (CommandEnum.EMPTY.equals(command)) {
			return CommandData.getEmpty();
		}
        CommandData commandData = new CommandData(command,
                sp.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, ""),
                TimelineTypeEnum.load(sp.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, "")),
                sp.getLong(IntentExtra.EXTRA_ITEMID.key + si, 0));
        commandData.bundle.putBoolean(IntentExtra.EXTRA_IN_FOREGROUND.key,
                sp.getBoolean(IntentExtra.EXTRA_IN_FOREGROUND.key + si, false));

        switch (commandData.command) {
            case FETCH_ATTACHMENT:
                commandData.bundle.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key,
                        sp.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key + si, ""));
                break;
            case UPDATE_STATUS:
                commandData.bundle.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key,
                        sp.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key + si, ""));
                commandData.bundle.putLong(IntentExtra.EXTRA_INREPLYTOID.key,
                        sp.getLong(IntentExtra.EXTRA_INREPLYTOID.key + si, 0));
                commandData.bundle.putLong(IntentExtra.EXTRA_RECIPIENTID.key,
                        sp.getLong(IntentExtra.EXTRA_RECIPIENTID.key + si, 0));
                commandData.bundle.putString(IntentExtra.EXTRA_MEDIA_URI.key,
                        sp.getString(IntentExtra.EXTRA_MEDIA_URI.key + si, ""));
                break;
			case SEARCH_MESSAGE:
				commandData.bundle.putString(IntentExtra.EXTRA_SEARCH_QUERY.key,
						sp.getString(IntentExtra.EXTRA_SEARCH_QUERY.key + si, ""));
				break;			
            default:
                break;
        }
        commandData.getResult().loadFromSharedPreferences(sp, index);
        return commandData;
    }
    
    /**
     * @return Number of items persisted
     */
    static int saveQueue(Context context, Queue<CommandData> queue, QueueType queueType) {
        String method = "saveQueue: ";
        int count = 0;
		SharedPreferences sp = MyPreferences.getSharedPreferences(queueType.getFileName());
		sp.edit().clear().commit();
        if (!queue.isEmpty()) {
            while (!queue.isEmpty()) {
                CommandData cd = queue.poll();
                cd.saveToSharedPreferences(sp, count);
                MyLog.v(context, method + "Command saved: " + cd.toString());
                count += 1;
            }
            MyLog.d(context, method + "to '" + queueType  + "', " + count + " msgs");
        }
		// TODO: How to clear all old shared preferences in this file?
		// Adding Empty command to mark the end.
		(new CommandData(CommandEnum.EMPTY, "")).saveToSharedPreferences(sp, count);
        return count;
    }
    
    /**
     * @return Number of items loaded
     */
    static int loadQueue(Context context, Queue<CommandData> q, QueueType queueType) {
        String method = "loadQueue: ";
		int count = 0;
        if (SharedPreferencesUtil.exists(context, queueType.getFileName())) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(queueType.getFileName());
			for (int index=0; index < 100000; index++) {
                CommandData cd = fromSharedPreferences(sp, index);
                if (CommandEnum.EMPTY.equals(cd.getCommand())) {
                    break;
				} else if (q.contains(cd)) {
					MyLog.e(context, method + index + " duplicate skipped " + cd);
					break;
                } else {
                    if ( q.offer(cd) ) {
                        MyLog.v(context, method + index + " " + cd);
						count++;
                    } else {
                        MyLog.e(context, method + index + " " + cd);
                    }
                }				
			}
            MyLog.d(context, method + "loaded " + count + " msgs from '" + queueType  + "'");
        }
        return count;
    }
    
    /**
     * It's used in equals() method. We need to distinguish duplicated
     * commands but to ignore differences in results!
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
            if (timelineType != TimelineTypeEnum.UNKNOWN) {
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
            if (getMediaUri() != null) {
                result += prime * getMediaUri().toString().hashCode();
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
        switch (command) {
            case UPDATE_STATUS:
                builder.append("\"");
                builder.append(I18n.trimTextAt(bundle.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key), 40));                
                builder.append("\",");
                if (getMediaUri() != null) {
                    builder.append("media=\"");
                    builder.append(getMediaUri().toString());                
                    builder.append("\",");
                }
                break;
            case SEARCH_MESSAGE:
                builder.append("\"");
                builder.append(I18n.trimTextAt(getSearchQuery(), 40));                
                builder.append("\",");
                break;
            default:
                break;
        }
        if (!TextUtils.isEmpty(getAccountName())) {
            builder.append("account:" + getAccountName() + ",");
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            builder.append(timelineType + ",");
        }
        if (itemId != 0) {
            builder.append("id:" + itemId + ",");
        }
        builder.append("hashCode:" + hashCode() + ",");
        builder.append(CommandResult.toString(commandResult));
        return MyLog.formatKeyValue("CommandData", builder);
    }

    /**
     * @return Intent to be sent to this.AndStatusService
     */
    public Intent toIntent(Intent intentIn) {
        Intent intent = intentIn;
        if (intent == null) {
            throw new IllegalArgumentException("toIntent: input intent is null");
        }
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putString(IntentExtra.EXTRA_MSGTYPE.key, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            bundle.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            bundle.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key, timelineType.save());
        }
        if (itemId != 0) {
            bundle.putLong(IntentExtra.EXTRA_ITEMID.key, itemId);
        }
        bundle.putBoolean(IntentExtra.EXTRA_IN_FOREGROUND.key, mInForeground);
        bundle.putBoolean(IntentExtra.EXTRA_MANUALLY_LAUCHED.key, mManuallyLaunched);
        bundle.putParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key, commandResult);        
        intent.putExtras(bundle);
        return intent;
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

    /**
     * Persist the object to the SharedPreferences 
     * We're not storing all types of commands here because not all commands
     *   go to the queue.
     * SharedPreferences should not contain any previous versions of the same entries 
     * (we don't store default values!)
     * @param sp
     * @param index Index of the preference's name to be used
     */
    private void saveToSharedPreferences(SharedPreferences sp, int index) {
        String si = Integer.toString(index);

        android.content.SharedPreferences.Editor ed = sp.edit();
        ed.putString(IntentExtra.EXTRA_MSGTYPE.key + si, command.save());
        ed.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, getAccountName());
        ed.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, timelineType.save());
        ed.putLong(IntentExtra.EXTRA_ITEMID.key + si, itemId);
        ed.putBoolean(IntentExtra.EXTRA_IN_FOREGROUND.key + si, mInForeground);
        ed.putBoolean(IntentExtra.EXTRA_MANUALLY_LAUCHED.key + si, mManuallyLaunched);
        switch (command) {
            case FETCH_ATTACHMENT:
                ed.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key + si, bundle.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key));
                break;
            case UPDATE_STATUS:
                ed.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key + si, bundle.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key));
                ed.putLong(IntentExtra.EXTRA_INREPLYTOID.key + si, bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key));
                ed.putLong(IntentExtra.EXTRA_RECIPIENTID.key + si, bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key));
                ed.putString(IntentExtra.EXTRA_MEDIA_URI.key + si, bundle.getString(IntentExtra.EXTRA_MEDIA_URI.key));
                break;
			case SEARCH_MESSAGE:
				ed.putString(IntentExtra.EXTRA_SEARCH_QUERY.key + si, getSearchQuery());
				break;
            default:
                break;
        }
        commandResult.saveToSharedPreferences(ed, index);
        ed.commit();
    }

    public String getAccountName() {
        return accountName;
    }

    /**
     * @return null if no MyAccount exists with supplied name
     */
    public MyAccount getAccount() {
        return MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
    }

    @Override
    public int compareTo(CommandData another) {
        int greater = 0;
        if ( another != null && another.priority != this.priority) {
            greater = this.priority > another.priority ? 1 : -1;
        }
        return greater;
    }

    public CommandEnum getCommand() {
        return command;
    }

    public TimelineTypeEnum getTimelineType() {
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

    public String toCommandSummary(MyContext myContext) {
        StringBuilder builder = new StringBuilder(toShortCommandName(myContext) + " ");
        switch (command) {
            case FETCH_AVATAR:
                builder.append(myContext.context().getText(R.string.combined_timeline_off_account) + " ");
                builder.append(MyProvider.userIdToName(itemId) );
                if (myContext.persistentAccounts().getDistinctOriginsCount() > 1) {
                    long originId = MyProvider.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, itemId);
                    builder.append(" " + myContext.context().getText(R.string.combined_timeline_off_origin) + " ");
                    builder.append(myContext.persistentOrigins().fromId(originId).getName() );
                }
                break;
            case FETCH_ATTACHMENT:
            case UPDATE_STATUS:
                builder.append("\"");
                builder.append(trimmedTextForSummary(bundle.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key)));                
                builder.append("\"");
                if (getMediaUri() != null) {
                    builder.append(" (" + MyContextHolder.get().context().getText(R.string.label_with_media).toString() + ")");
                }
                break;
            case AUTOMATIC_UPDATE:
            case FETCH_TIMELINE:
                if (!TextUtils.isEmpty(accountName)) {
                    if (timelineType == TimelineTypeEnum.USER) {
                        builder.append(MyProvider.userIdToName(itemId) + " ");
                    }
                    builder.append(timelineType.getPrepositionForNotCombinedTimeline(myContext.context()));
                    MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
                    if (ma == null) {
                        builder.append(" ('" + accountName + "' ?)");
                    } else {
                        builder.append(" " + TimelineActivity.buildAccountButtonText(ma.getUserId(), false, timelineType));
                    }
                }
                break;
			case SEARCH_MESSAGE:
				builder.append("\"");
                builder.append(I18n.trimTextAt(getSearchQuery(), 40));                
                builder.append("\" ");
				if (!TextUtils.isEmpty(accountName)) {
                    builder.append(myContext.context().getText(R.string.combined_timeline_off_origin) + " ");
                    MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
                    builder.append(ma != null ? ma.getOriginName() : "?");
                }
				break;
            default:
                if (!TextUtils.isEmpty(accountName)) {
                    builder.append(timelineType.getPrepositionForNotCombinedTimeline(myContext
                            .context())
                            + " ");
                    if (timelineType.atOrigin()) {
                        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
                        builder.append(ma != null ? ma.getOriginName() : "?");
                    } else {
                        builder.append(accountName);
                    }
                }
                break;
        }
        return builder.toString();
    }

    private static CharSequence trimmedTextForSummary(String text) {
        return I18n.trimTextAt(MyHtml.fromHtml(text), 40);
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
    
}
