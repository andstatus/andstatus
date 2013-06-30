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
 * @author yvolk
 */
public class CommandData {
    private static final String TAG = CommandData.class.getSimpleName();
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
        bundle.putString(MyService.EXTRA_PREFERENCE_KEY, preferenceKey);
        bundle.putBoolean(MyService.EXTRA_PREFERENCE_VALUE, value);
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
        bundle.putString(MyService.EXTRA_PREFERENCE_KEY, preferenceKey);
        bundle.putLong(MyService.EXTRA_PREFERENCE_VALUE, value);
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
        bundle.putString(MyService.EXTRA_PREFERENCE_KEY, preferenceKey);
        bundle.putString(MyService.EXTRA_PREFERENCE_VALUE, value);
    }

    /**
     * Used to decode command from the Intent upon receiving it
     * 
     * @param intent
     */
    public CommandData(Intent intent) {
        bundle = intent.getExtras();
        // Decode command
        String strCommand = "(no command)";
        if (bundle != null) {
            strCommand = bundle.getString(MyService.EXTRA_MSGTYPE);
            setAccountName(bundle.getString(MyService.EXTRA_ACCOUNT_NAME));
            timelineType = TimelineTypeEnum.load(bundle.getString(MyService.EXTRA_TIMELINE_TYPE));
            itemId = bundle.getLong(MyService.EXTRA_ITEMID);
        }
        command = CommandEnum.load(strCommand);
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
        String strCommand = sp.getString(MyService.EXTRA_MSGTYPE + si, CommandEnum.UNKNOWN.save());
        setAccountName(sp.getString(MyService.EXTRA_ACCOUNT_NAME + si, ""));
        timelineType = TimelineTypeEnum.load(sp.getString(MyService.EXTRA_TIMELINE_TYPE + si, ""));
        itemId = sp.getLong(MyService.EXTRA_ITEMID + si, 0);
        command = CommandEnum.load(strCommand);

        switch (command) {
            case UPDATE_STATUS:
                bundle.putString(MyService.EXTRA_STATUS, sp.getString(MyService.EXTRA_STATUS + si, ""));
                bundle.putLong(MyService.EXTRA_INREPLYTOID, sp.getLong(MyService.EXTRA_INREPLYTOID + si, 0));
                bundle.putLong(MyService.EXTRA_RECIPIENTID, sp.getLong(MyService.EXTRA_RECIPIENTID + si, 0));
                break;
        }

        MyLog.v(TAG, "Restored command " + (MyService.EXTRA_MSGTYPE + si) + " = " + strCommand);
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
                    text += bundle.getString(MyService.EXTRA_STATUS);
                    break;
                case PUT_BOOLEAN_PREFERENCE:
                    text += bundle.getString(MyService.EXTRA_PREFERENCE_KEY)
                            + bundle.getBoolean(MyService.EXTRA_PREFERENCE_VALUE);
                    break;
                case PUT_LONG_PREFERENCE:
                    text += bundle.getString(MyService.EXTRA_PREFERENCE_KEY)
                            + bundle.getLong(MyService.EXTRA_PREFERENCE_VALUE);
                    break;
                case PUT_STRING_PREFERENCE:
                    text += bundle.getString(MyService.EXTRA_PREFERENCE_KEY)
                            + bundle.getString(MyService.EXTRA_PREFERENCE_VALUE);
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
                + (itemId == 0 ? "" : "; id=" + itemId) + ", hashCode=" + hashCode() + "]";
    }

    /**
     * @return Intent to be sent to this.AndStatusService
     */
    public Intent toIntent() {
        return toIntent(null);
    }

    /**
     * @return Intent to be sent to this.AndStatusService
     */
    public Intent toIntent(Intent intent_in) {
        Intent intent = intent_in;
        if (intent == null) {
            intent = new Intent(MyService.ACTION_GO);
        }
        if (bundle == null) {
            bundle = new Bundle();
        }
        bundle.putString(MyService.EXTRA_MSGTYPE, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            bundle.putString(MyService.EXTRA_ACCOUNT_NAME, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            bundle.putString(MyService.EXTRA_TIMELINE_TYPE, timelineType.save());
        }
        if (itemId != 0) {
            bundle.putLong(MyService.EXTRA_ITEMID, itemId);
        }
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
        ed.putString(MyService.EXTRA_MSGTYPE + si, command.save());
        if (!TextUtils.isEmpty(getAccountName())) {
            ed.putString(MyService.EXTRA_ACCOUNT_NAME + si, getAccountName());
        }
        if (timelineType != TimelineTypeEnum.UNKNOWN) {
            ed.putString(MyService.EXTRA_TIMELINE_TYPE + si, timelineType.save());
        }
        if (itemId != 0) {
            ed.putLong(MyService.EXTRA_ITEMID + si, itemId);
        }
        switch (command) {
            case UPDATE_STATUS:
                ed.putString(MyService.EXTRA_STATUS + si, bundle.getString(MyService.EXTRA_STATUS));
                ed.putLong(MyService.EXTRA_INREPLYTOID + si, bundle.getLong(MyService.EXTRA_INREPLYTOID));
                ed.putLong(MyService.EXTRA_RECIPIENTID + si, bundle.getLong(MyService.EXTRA_RECIPIENTID));
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
        return MyAccount.fromAccountName(accountName);
    }
    
    private void setAccountName(String accountName) {
        this.accountName = accountName;
    }
}