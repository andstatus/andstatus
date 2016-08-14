package org.andstatus.app.account;

import android.accounts.Account;
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
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class PersistentAccounts {
    /**
     * Name of "current" account: it is not stored when application is killed
     */
    private volatile String currentAccountName = "";

    private final MyContext myContext;
    private final List<MyAccount> mAccounts = new CopyOnWriteArrayList<>();
    private int distinctOriginsCount = 0;
    private volatile Set<Long> myFriends = null;

    private PersistentAccounts(MyContext myContext) {
        this.myContext = myContext;
    }
    
    /**
     * Get list of all persistent accounts
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link MyAccount#getCredentialsVerified()} is the main differentiator.
     * 
     * @return not null 
     */
    public List<MyAccount> list() {
        return mAccounts;
    }
    
    public boolean isEmpty() {
        return mAccounts.isEmpty();
    }
    
    public int size() {
        return mAccounts.size();
    }
    
    public PersistentAccounts initialize() {
        myFriends = null;
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        List<MyAccount> myAccounts = new ArrayList<>();
        for (android.accounts.Account account : aa) {
            MyAccount ma = Builder.fromAndroidAccount(myContext, account).getAccount();
            if (ma.isValid()) {
                myAccounts.add(ma);
            } else {
                MyLog.e(this, "The account is not valid: " + ma);
            }
        }
        CollectionsUtil.sort(myAccounts);
        mAccounts.clear();
        mAccounts.addAll(myAccounts);
        calculateDistinctOriginsCount();
        MyLog.v(this, "Account list initialized, " + mAccounts.size() + " accounts in " + distinctOriginsCount + " origins");
        return this;
    }

    public MyAccount getDefaultAccount() {
        return mAccounts.isEmpty() ? MyAccount.getEmpty(myContext) : list().get(0);
    }

    public int getDistinctOriginsCount() {
        return distinctOriginsCount;
    }
    
    private void calculateDistinctOriginsCount() {
        Set<Long> originIds = new HashSet<>();
        for (MyAccount ma : mAccounts) {
            originIds.add(ma.getOriginId());
        }
        distinctOriginsCount = originIds.size();
    }
    
    public static PersistentAccounts newEmpty(MyContext myContext) {
        return new PersistentAccounts(myContext);
    }
    
    /**
     * Delete everything about the MyAccount
     * 
     * @return Was the MyAccount (and Account) deleted?
     */
    public boolean delete(MyAccount ma) {
        boolean isDeleted = false;

        // Delete the User's object from the list
        MyAccount toDelete = null;
        for (MyAccount persistentAccount : mAccounts) {
            if (persistentAccount.equals(ma)) {
                toDelete = persistentAccount;
                break;
            }
        }
        if (toDelete != null) {
            MyAccount.Builder.fromMyAccount(myContext, ma, "delete", false).deleteData();

            // And delete the object from the list
            mAccounts.remove(toDelete);

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
        MyAccount myAccount = MyAccount.getEmpty(myContext, accountNameIn);
        if (!myAccount.isUsernameValid()) {
            return myAccount;
        }

        for (MyAccount persistentAccount : mAccounts) {
            if (persistentAccount.getAccountName().compareTo(myAccount.getAccountName()) == 0) {
                myAccount = persistentAccount;
                break;
            }
        }
        // Try to find persisted Account which was not loaded yet
        if (!myAccount.isValid()) {
            android.accounts.Account[] androidAccounts = AccountManager.get(
                    myContext.context()).getAccountsByType(
                    AuthenticatorService.ANDROID_ACCOUNT_TYPE);
            for (android.accounts.Account androidAccount : androidAccounts) {
                if (myAccount.getAccountName().compareTo(androidAccount.name) == 0) {
                    myAccount = Builder.fromAndroidAccount(myContext, androidAccount)
                            .getAccount();
                    mAccounts.add(myAccount);
                    CollectionsUtil.sort(mAccounts);
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
        ma = getDefaultAccount();
        if (!ma.isValid()) {
            for (MyAccount myAccount : mAccounts) {
                if (myAccount.isValid()) {
                    ma = myAccount;
                    break;
                }
            }
        }
        if (ma.isValid()) {
            // Correct Current Account if needed
            if (TextUtils.isEmpty(currentAccountName)) {
                setCurrentAccount(ma);
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

    public boolean isAccountUserId(long userId) {
        if (userId == 0) {
            return false;
        }
        return fromUserId(userId).isValid();
    }

    /**
     * Get MyAccount by the UserId. 
     * Please note that a valid User may not have an Account (in AndStatus)
     * @return Invalid account if was not found
     */
    @NonNull
    public MyAccount fromUserId(long userId) {
        MyAccount ma = MyAccount.getEmpty(myContext, "(id=" + userId +")");
        if (userId != 0) {
            for (MyAccount persistentAccount : mAccounts) {
                if (persistentAccount.getUserId() == userId) {
                    ma = persistentAccount;
                    break;
                }
            }
        }
        return ma;
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided originId.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param originId May be 0 to search in any Origin
     * @return Invalid account if not found
     */
    public MyAccount getFirstSucceededForOriginId(long originId) {
        MyAccount ma = MyAccount.getEmpty(myContext, "");
        for (MyAccount persistentAccount : mAccounts) {
            if (originId==0 || persistentAccount.getOriginId() == originId) {
                if (!ma.isValid()) {
                    ma = persistentAccount;
                }
                if (persistentAccount.isValidAndSucceeded()) {
                    if (!ma.isValidAndSucceeded()) {
                        ma = persistentAccount;
                    }
                    if (persistentAccount.isSyncedAutomatically()) {
                        ma = persistentAccount;
                        break;
                    }
                }
            }
        }
        return ma;
    }

    public boolean hasSyncedAutomatically() {
        for (MyAccount ma : mAccounts) {
            if (ma.isValidAndSucceeded() && ma.isSyncedAutomatically()) {
                return true;
            }
        }
        return false;
    }

    /** Should not be called from UI thread
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
            ma = betterFit(ma, getFirstSucceededForOriginId(originId), originId, succeededOnly);
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

    public void onDefaultSyncFrequencyChanged() {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount ma : mAccounts) {
            if (ma.getSyncFrequencySeconds() <= 0) {
                Account account = ma.getExistingAndroidAccount();
                if (account != null) {
                    AccountData.setSyncFrequencySeconds(account, syncFrequencySeconds);
                }
            }
        }
    }

    public List<MyAccount> accountsToSync(MyAccount myAccount, boolean forAllAccounts) {
        boolean hasSyncedAutomatically = hasSyncedAutomatically();
        List<MyAccount> accounts = new ArrayList<>();
        if (forAllAccounts) {
            for (MyAccount account : list()) {
                addMyAccountToSync(accounts, account, hasSyncedAutomatically);
            }
        } else {
            addMyAccountToSync(accounts, myAccount, false);
        }
        return accounts;
    }

    private void addMyAccountToSync(List<MyAccount> accounts, MyAccount account, boolean hasSyncedAutomatically) {
        if ( !account.isValidAndSucceeded()) {
            MyLog.v(this, "Account '" + account.getAccountName() + "' skipped as invalid authenticated account");
            return;
        }
        if (hasSyncedAutomatically && !account.isSyncedAutomatically()) {
            MyLog.v(this, "Account '" + account.getAccountName() + "' skipped as it is not synced automatically");
            return;
        }
        accounts.add(account);
    }

    public static final String KEY_ACCOUNT = "account";
    public long onBackup(MyBackupDataOutput data, MyBackupDescriptor newDescriptor) throws IOException {
        long backedUpCount = 0;
        JSONArray jsa = new JSONArray();
        try {
            for (MyAccount ma : mAccounts) {
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
    public String toString() {
        return "PersistentAccounts{" +
                "mAccounts=" + mAccounts +
                '}';
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
        SQLiteDatabase db = myContext.getDatabase();
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

    public void reorderAccounts(List<MyAccount> reorderedItems) {
        int order = 0;
        boolean changed = false;
        for (MyAccount myAccount : reorderedItems) {
            order++;
            if (myAccount.getOrder() != order) {
                changed = true;
                MyAccount.Builder builder = Builder.fromMyAccount(myContext, myAccount, "reorder", false);
                builder.setOrder(order);
                builder.save();
            }
        }
        if (changed) {
            CollectionsUtil.sort(mAccounts);
            MyPreferences.onPreferencesChanged();
        }
    }
}
