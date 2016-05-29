package org.andstatus.app.account;

import android.accounts.AccountManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.Builder;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.backup.MyBackupDataInput;
import org.andstatus.app.backup.MyBackupDataOutput;
import org.andstatus.app.backup.MyBackupDescriptor;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentAccounts {
    /**
     * Persistence key for the Name of the default account
     */
    public static final String KEY_DEFAULT_ACCOUNT_NAME = "default_account_name";
    /**
     * Name of the default account. The name is the same for this class and for {@link android.accounts.Account}
     */
    private volatile String defaultAccountName = "";
    /**
     * Name of "current" account: it is not stored when application is killed
     */
    private volatile String currentAccountName = "";
    
    private final Map<String,MyAccount> mAccounts = new ConcurrentHashMap<>();
    private int distinctOriginsCount = 0;
    private volatile Set<Long> myFriends = null;

    private PersistentAccounts() {
    }
    
    /**
     * Get list of all persistent accounts
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link MyAccount#getCredentialsVerified()} is the main differentiator.
     * 
     * @return not null 
     */
    public Collection<MyAccount> collection() {
        return mAccounts.values();
    }
    
    public boolean isEmpty() {
        return mAccounts.isEmpty();
    }
    
    public int size() {
        return mAccounts.size();
    }
    
    public PersistentAccounts initialize() {
        return initialize(MyContextHolder.get());
    }
    
    public PersistentAccounts initialize(MyContext myContext) {
        defaultAccountName = getDefaultAccountName();
        mAccounts.clear();
        myFriends = null;
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account account : aa) {
            MyAccount ma = Builder.fromAndroidAccount(myContext, account).getAccount();
            if (ma.isValid()) {
                mAccounts.put(ma.getAccountName(), ma);
            } else {
                MyLog.e(this, "The account is not valid: " + ma);
            }
        }
        calculateDistinctOriginsCount();
        MyLog.v(this, "Account list initialized, " + mAccounts.size() + " accounts in " + distinctOriginsCount + " origins");
        return this;
    }

    public static Set<AccountData> getAccountDataFromAccountManager(MyContext myContext) {
        Set<AccountData> accountDataSet = new HashSet<>();
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account androidAccount : aa) {
            accountDataSet.add(AccountData.fromAndroidAccount(myContext, androidAccount));
        }
        return accountDataSet;
    }

    public String getDefaultAccountName() {
        return SharedPreferencesUtil.getString(KEY_DEFAULT_ACCOUNT_NAME, "");
    }

    public int getDistinctOriginsCount() {
        return distinctOriginsCount;
    }
    
    private void calculateDistinctOriginsCount() {
        Set<Long> originIds = new HashSet<>();
        for (MyAccount ma : mAccounts.values()) {
            originIds.add(ma.getOriginId());
        }
        distinctOriginsCount = originIds.size();
    }
    
    public static PersistentAccounts getEmpty() {
        return new PersistentAccounts();
    }
    
    /**
     * Delete everything about the MyAccount
     * 
     * @return Was the MyAccount (and Account) deleted?
     */
    public boolean delete(MyAccount ma) {
        boolean isDeleted = false;

        // Delete the User's object from the list
        boolean found = false;
        for (MyAccount persistentAccount : mAccounts.values()) {
            if (persistentAccount.equals(ma)) {
                found = true;
                break;
            }
        }
        if (found) {
            MyAccount.Builder.fromMyAccount(MyContextHolder.get(), ma, "delete", false).deleteData();

            // And delete the object from the list
            mAccounts.remove(ma.getAccountName());

            isDeleted = true;
            MyPreferences.onPreferencesChanged();
        }
        return isDeleted;
    }

    /**
     * Find persistent MyAccount by accountName in local cache AND in Android
     * AccountManager
     * 
     * @return Invalid account if was not found
     */
    public MyAccount fromAccountName(String accountNameIn) {
        MyAccount myAccount = MyAccount.getEmpty(MyContextHolder.get(), accountNameIn);
        if (!myAccount.isUsernameValid()) {
            return myAccount;
        }

        for (MyAccount persistentAccount : mAccounts.values()) {
            if (persistentAccount.getAccountName().compareTo(myAccount.getAccountName()) == 0) {
                myAccount = persistentAccount;
                break;
            }
        }
        // Try to find persisted Account which was not loaded yet
        if (!myAccount.isValid()) {
            android.accounts.Account[] androidAccounts = AccountManager.get(
                    MyContextHolder.get().context()).getAccountsByType(
                    AuthenticatorService.ANDROID_ACCOUNT_TYPE);
            for (android.accounts.Account androidAccount : androidAccounts) {
                if (myAccount.getAccountName().compareTo(androidAccount.name) == 0) {
                    myAccount = Builder.fromAndroidAccount(MyContextHolder.get(), androidAccount)
                            .getAccount();
                    mAccounts.put(myAccount.getAccountName(), myAccount);
                    MyPreferences.onPreferencesChanged();
                    break;
                }
            }
        }
        return myAccount;
    }

    
    /**
     * Get instance of current MyAccount (MyAccount selected by the user). The account isPersistent.
     * As a side effect the function changes current account if old value is not valid.
     * @return Invalid account if no persistent accounts exist
     */
    @NonNull
    public MyAccount getCurrentAccount() {
        MyAccount ma = fromAccountName(currentAccountName);
        if (ma.isValid()) {
            return ma;
        }
        currentAccountName = "";
        ma = fromAccountName(defaultAccountName);
        if (!ma.isValid()) {
            defaultAccountName = "";
        }
        if (!ma.isValid()) {
            for (MyAccount myAccount : mAccounts.values()) {
                if (myAccount.isValid()) {
                    ma = myAccount;
                    break;
                }
            }
        }
        if (ma.isValid()) {
            // Correct Current and Default Accounts if needed
            if (TextUtils.isEmpty(currentAccountName)) {
                setCurrentAccount(ma);
            }
            if (TextUtils.isEmpty(defaultAccountName)) {
                setDefaultAccount(ma);
            }
        }
        return ma;
    }
    
    /**
     * Get Guid of current MyAccount (MyAccount selected by the user). The account isPersistent
     */
    public String getCurrentAccountName() {
        return getCurrentAccount().getAccountName();
    }
    
    /**
     * @return 0 if no valid persistent accounts exist
     */
    public long getCurrentAccountUserId() {
        return getCurrentAccount().getUserId();
    }

    public boolean isAccountUserId(long selectedUserId) {
        return fromUserId(selectedUserId).isValid();
    }

    /**
     * Get MyAccount by the UserId. 
     * Please note that a valid User may not have an Account (in AndStatus)
     * @return Invalid account if was not found
     */
    @NonNull
    public MyAccount fromUserId(long userId) {
        MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "(id=" + userId +")");
        if (userId != 0) {
            for (MyAccount persistentAccount : mAccounts.values()) {
                if (persistentAccount.getUserId() == userId) {
                    ma = persistentAccount;
                    break;
                }
            }
        }
        return ma;
    }

    /**
     * Return first verified MyAccount of the provided originId.
     * If there is no verified account, any account of this Origin is been returned.
     * @param originId May be 0 to search in any Origin
     * @return Invalid account if not found
     */
    public MyAccount findFirstSucceededMyAccountByOriginId(long originId) {
        MyAccount ma = null;
        for (MyAccount persistentAccount : mAccounts.values()) {
            if (originId==0 || persistentAccount.getOriginId() == originId) {
                if (persistentAccount.isValidAndSucceeded()) {
                    ma = persistentAccount;
                    break;
                }
                if (ma == null) {
                    ma = persistentAccount;
                }
            }
        }
        if (ma == null) {
            ma = MyAccount.getEmpty(MyContextHolder.get(), "");
        }
        return ma;
    }

    public boolean hasSyncedAutomatically() {
        for (MyAccount ma : mAccounts.values()) {
            if (ma.isValidAndSucceeded() && ma.isSyncedAutomatically()) {
                return true;
            }
        }
        return false;
    }

    /** Should not be called from UI thread */
    public MyAccount getAccountForThisMessage(long messageId, long firstUserId,
                                              long preferredUserId, boolean succeededOnly) {
        return getAccountForThisMessage(MyQuery.msgIdToOriginId(messageId), messageId, firstUserId, preferredUserId, succeededOnly);
    }

    /**
     * Find MyAccount, which may be linked to this message. 
     * First try two supplied user IDs, then try any other existing account
     * @return Invalid account if nothing suitable found
     */
    public MyAccount getAccountForThisMessage(long originId, long messageId, long firstUserId,
            long preferredUserId, boolean succeededOnly)  {
        final String method = "getAccountForThisMessage";
        MyAccount ma = null;
        if (originId != 0) {
            ma = fromUserId(firstUserId);
        }
        if (!accountFits(ma, originId, succeededOnly)) {
            ma = betterFit(ma, fromUserId(preferredUserId), originId, succeededOnly);
        }
        if (!accountFits(ma, originId, succeededOnly)) {
            ma = betterFit(ma, findFirstSucceededMyAccountByOriginId(originId), originId, succeededOnly);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; msgId=" + messageId 
                    + "; userId1=" + firstUserId 
                    + "; userId2=" + preferredUserId
                    + (succeededOnly ? "; succeeded only" : "")
                    + " -> account=" + ma.getAccountName());
        }
        return ma;
    }

    private boolean accountFits(MyAccount ma, long originId, boolean succeededOnly) {
        return ma != null
                && (succeededOnly ? ma.isValidAndSucceeded() : ma.isValid())
                && (originId == 0 || ma.getOriginId() == originId);
    }
    
    private MyAccount betterFit(MyAccount oldMa, MyAccount newMa, long originId, boolean succeededOnly) {
        if (accountFits(oldMa, originId, succeededOnly) || !accountFits(newMa, originId, false)) {
            return oldMa;
        }
        if ((oldMa == null || !oldMa.isValid()) && newMa.isValid()) {
            return newMa;
        }
        return oldMa;
    }
    
    /**
     * Set provided MyAccount as Current one.
     * Current account selection is not persistent
     */
    public void setCurrentAccount(MyAccount ma) {
        if (ma != null && !currentAccountName.equals(ma.getAccountName()) ) {
            MyLog.v(this, "Changing current account from '" + currentAccountName + "' to '" + ma.getAccountName() + "'");
            currentAccountName = ma.getAccountName();
        }
    }

    /**
     * Set provided MyAccount as a default one.
     * Default account selection is persistent
     */
    public void setDefaultAccount(MyAccount ma) {
        if (ma != null) {
            defaultAccountName = ma.getAccountName();
        }
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
    }
    
    public void onMyPreferencesChanged(MyContext myContext) {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount ma : mAccounts.values()) {
            Builder builder = Builder.fromMyAccount(myContext, ma, "onMyPreferencesChanged", false);
            builder.setSyncFrequency(syncFrequencySeconds);
            builder.save();
        }
    }

    public boolean isGlobalSearchSupported(MyAccount ma, boolean forAllAccounts) {
        boolean yes = false;
        if (forAllAccounts) {
            for (MyAccount ma1 : collection()) {
                if (ma1.isGlobalSearchSupported()) {
                    yes = true;
                    break;
                }
            }
        } else {
            yes = ma.isGlobalSearchSupported();
        }
        return yes;
    }
    
    public static final String KEY_ACCOUNT = "account";
    public long onBackup(MyBackupDataOutput data, MyBackupDescriptor newDescriptor) throws IOException {
        long backedUpCount = 0;
        JSONArray jsa = new JSONArray();
        try {
            for (MyAccount ma : mAccounts.values()) {
                jsa.put(ma.toJson());
                backedUpCount++;
            }
            byte[] bytes = jsa.toString(2).getBytes("UTF-8");
            data.writeEntityHeader(KEY_ACCOUNT, bytes.length, ".json");
            data.writeEntityData(bytes, bytes.length);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        newDescriptor.setAccountsCount(backedUpCount);
        return backedUpCount;
    }

    /** Returns count of restores objects */
    public long onRestore(MyBackupDataInput data, MyBackupDescriptor newDescriptor) throws IOException {
        long restoredCount = 0;
        final String method = "onRestore";
        MyLog.i(this, method + "; started, " + I18n.formatBytes(data.getDataSize()));
        byte[] bytes = new byte[data.getDataSize()];
        int bytesRead = data.readEntityData(bytes, 0, bytes.length);
        try {
            JSONArray jsa = new JSONArray(new String(bytes, 0, bytesRead, "UTF-8"));
            for (int ind = 0; ind < jsa.length(); ind++) {
                MyLog.v(this, method + "; restoring " + (ind+1) + " of " + jsa.length());
                MyAccount.Builder builder = Builder.fromJson(data.getMyContext(), (JSONObject) jsa.get(ind));
                CredentialsVerificationStatus verified = builder.getAccount().getCredentialsVerified(); 
                if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                    newDescriptor.getLogger().logProgress("Account " + builder.getAccount().getAccountName() + " was not successfully verified");
                    builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
                }
                if (builder.saveSilently().success) {
                    MyLog.v(this, method + "; restored " + (ind+1) + ": " + builder.toString());
                    restoredCount++;
                    if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                        builder.setCredentialsVerificationStatus(verified);
                        builder.saveSilently();
                    }
                } else {
                    MyLog.e(this, method + "; failed to restore " + (ind+1) + ": " + builder.toString());
                }
            }
            if (restoredCount != newDescriptor.getAccountsCount()) {
                throw new FileNotFoundException("Restored only " + restoredCount + " accounts of " + newDescriptor.getAccountsCount());
            }
            newDescriptor.getLogger().logProgress("Restored " + restoredCount + " accounts");
        } catch (JSONException e) {
            throw new IOException(method, e);
        }
        return restoredCount;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((mAccounts == null) ? 0 : mAccounts.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PersistentAccounts other = (PersistentAccounts) obj;
        if (mAccounts == null) {
            if (other.mAccounts != null) {
                return false;
            }
        } else if (!mAccounts.equals(other.mAccounts)) {
            return false;
        }
        return true;
    }

    public boolean isMeOrMyFriend(long inReplyToUserId) {
        if (isAccountUserId(inReplyToUserId)) {
            return true;
        }
        return isMyFriend(inReplyToUserId);
    }

    private boolean isMyFriend(long userId) {
        if (myFriends == null) {
            initializeMyFriends();
        }
        return myFriends.contains(userId);
    }

    private void initializeMyFriends() {
        Set<Long> friends = new HashSet<>();
        String sql = "SELECT DISTINCT " + FriendshipTable.FRIEND_ID + " FROM " + FriendshipTable.TABLE_NAME
                + " WHERE " + FriendshipTable.FOLLOWED + "=1";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                friends.add(cursor.getLong(0));
            }
        } catch (Exception e) {
            MyLog.i(this, "SQL:'" + sql + "'", e);
        } finally {
            DbUtils.closeSilently(cursor);
        }
        myFriends = friends;
    }

    public long getDefaultAccountUserId() {
        return fromAccountName(getDefaultAccountName()).getUserId();
    }
}
