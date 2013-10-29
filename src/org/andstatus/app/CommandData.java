/**
 * Copyright (C) 2010-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

/**
 * Command data store (message...)
 * 
 * @author yvolk@yurivolkov.com
 */
public class CommandData {
    public CommandEnum command;
    
    /**
     * Unique name of {@link MyAccount} for this command. Empty string if command is not Account specific 
     * (e.g. {@link CommandEnum#AUTOMATIC_UPDATE} which works for all accounts) 
     */
    private String accountName = "";

    /**
     * Timeline type used for the {@link CommandEnum#FETCH_TIMELINE} command 
     */
    public MyDatabase.TimelineTypeEnum timelineType = TimelineTypeEnum.UNKNOWN;
    
    /**
     * This is: 
     * 1. Generally: Message ID ({@link MyDatabase.Msg#MSG_ID} of the {@link MyDatabase.Msg}).
     * 2. User ID ( {@link MyDatabase.User#USER_ID} ) for the {@link CommandEnum#FETCH_USER_TIMELINE}, 
     *      {@link CommandEnum#FOLLOW_USER}, {@link CommandEnum#STOP_FOLLOWING_USER} 
     */
    public long itemId = 0;

    /**
     * Other command parameters
     */
    public Bundle bundle = new Bundle();

    private int hashcode = 0;

    /**
     * Number of retries left
     */
    public int retriesLeft = 0;

    public CommandResult commandResult = new CommandResult();
    
    public static final CommandData EMPTY_COMMAND = new CommandData(CommandEnum.EMPTY, "");
    
    public CommandData(CommandEnum commandIn, String accountNameIn) {
        command = commandIn;
        if (!TextUtils.isEmpty(accountNameIn)) {
            setAccountName(accountNameIn);
        }
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, long itemIdIn) {
        this(commandIn, accountNameIn);
        itemId = itemIdIn;
    }

    public CommandData(CommandEnum commandIn, String accountNameIn, TimelineTypeEnum timelineTypeIn, long itemIdIn) {
        this(commandIn, accountNameIn, itemIdIn);
        timelineType = timelineTypeIn;
    }

    /**
     * Initialize command to put boolean SharedPreference
     * 
     * @param preferenceKey
     * @param value
     * @param accountNameIn - preferences for this user, or null if Global
     *            preferences
     */
    public CommandData(String accountNameIn, String preferenceKey, boolean value) {
        this(CommandEnum.PUT_BOOLEAN_PREFERENCE, accountNameIn);
        bundle.putString(IntentExtra.EXTRA_PREFERENCE_KEY.key, preferenceKey);
        bundle.putBoolean(IntentExtra.EXTRA_PREFERENCE_VALUE.key, value);
    }

    /**
     * Initialize command to put long SharedPreference
     * 
     * @param accountNameIn - preferences for this user, or null if Global
     *            preferences
     * @param preferenceKey
     * @param value
     */
    public CommandData(String accountNameIn, String preferenceKey, long value) {
        this(CommandEnum.PUT_LONG_PREFERENCE, accountNameIn);
        bundle.putString(IntentExtra.EXTRA_PREFERENCE_KEY.key, preferenceKey);
        bundle.putLong(IntentExtra.EXTRA_PREFERENCE_VALUE.key, value);
    }

    /**
     * Initialize command to put string SharedPreference
     * 
     * @param accountNameIn - preferences for this user
     * @param preferenceKey
     * @param value
     */
    public CommandData(String accountNameIn, String preferenceKey, String value) {
        this(CommandEnum.PUT_STRING_PREFERENCE, accountNameIn);
        bundle.putString(IntentExtra.EXTRA_PREFERENCE_KEY.key, preferenceKey);
        bundle.putString(IntentExtra.EXTRA_PREFERENCE_VALUE.key, value);
    }

    private CommandData() {
    }

    /**
     * Used to decode command from the Intent upon receiving it
     */
    public static CommandData fromIntent(Intent intent) {
        CommandData commandData;
        if (intent == null) {
            commandData = EMPTY_COMMAND;
        } else {
            commandData = new CommandData();
            commandData.bundle = intent.getExtras();
            // Decode command
            String strCommand = "(no command)";
            if (commandData.bundle != null) {
                strCommand = commandData.bundle.getString(IntentExtra.EXTRA_MSGTYPE.key);
                commandData.setAccountName(commandData.bundle.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                commandData.timelineType = TimelineTypeEnum.load(commandData.bundle.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key));
                commandData.itemId = commandData.bundle.getLong(IntentExtra.EXTRA_ITEMID.key);
                commandData.commandResult = commandData.bundle.getParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key);
            }
            commandData.command = CommandEnum.load(strCommand);
        }
        return commandData;
    }
    
    /**
     * Restore this from the SharedPreferences 
     * @param sp
     * @param index Index of the preference's name to be used
     */
    public CommandData(SharedPreferences sp, int index) {
        bundle = new Bundle();
        String si = Integer.toString(index);
        // Decode command
        String strCommand = sp.getString(IntentExtra.EXTRA_MSGTYPE.key + si, CommandEnum.UNKNOWN.save());
        setAccountName(sp.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, ""));
        timelineType = TimelineTypeEnum.load(sp.getString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, ""));
        itemId = sp.getLong(IntentExtra.EXTRA_ITEMID.key + si, 0);
        command = CommandEnum.load(strCommand);

        switch (command) {
            case UPDATE_STATUS:
                bundle.putString(IntentExtra.EXTRA_STATUS.key, sp.getString(IntentExtra.EXTRA_STATUS.key + si, ""));
                bundle.putLong(IntentExtra.EXTRA_INREPLYTOID.key, sp.getLong(IntentExtra.EXTRA_INREPLYTOID.key + si, 0));
                bundle.putLong(IntentExtra.EXTRA_RECIPIENTID.key, sp.getLong(IntentExtra.EXTRA_RECIPIENTID.key + si, 0));
                break;
            default:
                break;
        }

        MyLog.v(this, "Restored command " + (IntentExtra.EXTRA_MSGTYPE + si) + " = " + strCommand);
    }
    
    /**
     * It's used in equals() method. We need to distinguish duplicated
     * commands
     */
    @Override
    public int hashCode() {
        if (hashcode == 0) {
            String text = Long.toString(command.ordinal());
            if (!TextUtils.isEmpty(getAccountName())) {
                text += getAccountName();
            }
            if (timelineType != TimelineTypeEnum.UNKNOWN) {
                text += timelineType.save();
            }
            if (itemId != 0) {
                text += Long.toString(itemId);
            }
            switch (command) {
                case UPDATE_STATUS:
                    text += bundle.getString(IntentExtra.EXTRA_STATUS.key);
                    break;
                case PUT_BOOLEAN_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getBoolean(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                case PUT_LONG_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getLong(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                case PUT_STRING_PREFERENCE:
                    text += bundle.getString(IntentExtra.EXTRA_PREFERENCE_KEY.key)
                            + bundle.getString(IntentExtra.EXTRA_PREFERENCE_VALUE.key);
                    break;
                default:
                    break;
            }
            hashcode = text.hashCode();
        }
        return hashcode;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "CommandData [" + "command=" + command.save()
                + (TextUtils.isEmpty(getAccountName()) ? "" : "; account=" + getAccountName())
                + (timelineType == TimelineTypeEnum.UNKNOWN ? "" : "; timeline=" + timelineType.save())
                + (itemId == 0 ? "" : "; id=" + itemId) + ", hashCode=" + hashCode()
                + (commandResult.hasError() ? (commandResult.hasHardError() ? "; Hard Error" : "; Soft Error") : "")
                + "]";
    }

    /**
     * @return Intent to be sent to this.AndStatusService
     */
    public Intent toIntent(Intent intent_in) {
        Intent intent = intent_in;
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
        bundle.putParcelable(IntentExtra.EXTRA_COMMAND_RESULT.key, commandResult);        
        intent.putExtras(bundle);
        return intent;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommandData)) {
            return false;
        }
        CommandData cd = (CommandData) o;
        return (hashCode() == cd.hashCode());
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
    public void save(SharedPreferences sp, int index) {
        String si = Integer.toString(index);

        android.content.SharedPreferences.Editor ed = sp.edit();
        ed.putString(IntentExtra.EXTRA_MSGTYPE.key + si, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            ed.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key + si, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            ed.putString(IntentExtra.EXTRA_TIMELINE_TYPE.key + si, timelineType.save());
        }
        if (itemId != 0) {
            ed.putLong(IntentExtra.EXTRA_ITEMID.key + si, itemId);
        }
        switch (command) {
            case UPDATE_STATUS:
                ed.putString(IntentExtra.EXTRA_STATUS.key + si, bundle.getString(IntentExtra.EXTRA_STATUS.key));
                ed.putLong(IntentExtra.EXTRA_INREPLYTOID.key + si, bundle.getLong(IntentExtra.EXTRA_INREPLYTOID.key));
                ed.putLong(IntentExtra.EXTRA_RECIPIENTID.key + si, bundle.getLong(IntentExtra.EXTRA_RECIPIENTID.key));
                break;
            default:
                break;
        }
        ed.commit();
    }

    private String getAccountName() {
        return accountName;
    }

    /**
     * @return null if no MyAccount exists with supplied name
     */
    public MyAccount getAccount() {
        return MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
    }
    
    private void setAccountName(String accountName) {
        this.accountName = accountName;
    }
    
    public void resetCommandResult() {
        commandResult = new CommandResult();
    }
}