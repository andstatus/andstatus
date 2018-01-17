package org.andstatus.app.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
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
import org.andstatus.app.data.SqlUserIds;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class PersistentAccounts {
    /**
     * Name of "current" account: it is not stored when application is killed
     */
    private volatile String currentAccountName = "";

    private final MyContext myContext;
    private final List<MyAccount> mAccounts = new CopyOnWriteArrayList<>();
    private int distinctOriginsCount = 0;
    private final Set<Long> myFriends = new ConcurrentSkipListSet<>();

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
        List<MyAccount> myAccounts = new ArrayList<>();
        for (android.accounts.Account account : getAccounts(myContext.context())) {
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
        initializeMyFriends();
        return this;
    }

    public MyAccount getDefaultAccount() {
        return mAccounts.isEmpty() ? MyAccount.EMPTY : list().get(0);
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
    @NonNull
    public MyAccount fromAccountName(String accountNameString) {
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        if (!accountName.isValid()) {
            return MyAccount.EMPTY;
        }
        for (MyAccount persistentAccount : mAccounts) {
            if (persistentAccount.getAccountName().equals(accountName.toString())) {
                return persistentAccount;
            }
        }
        for (android.accounts.Account androidAccount : getAccounts(myContext.context())) {
            if (accountName.toString().equals(androidAccount.name)) {
                MyAccount myAccount = Builder.fromAndroidAccount(myContext, androidAccount).getAccount();
                mAccounts.add(myAccount);
                CollectionsUtil.sort(mAccounts);
                MyPreferences.onPreferencesChanged();
                return myAccount;
            }
        }
        return MyAccount.EMPTY;
    }

    @NonNull
    public MyAccount fromUserOfSameOrigin(@NonNull MbUser user) {
        final boolean webFingerIdValid = user.isWebFingerIdValid();
        for (MyAccount persistentAccount : mAccounts) {
            if (persistentAccount.getOriginId() == user.originId) {
                if (StringUtils.nonEmpty(user.oid)) {
                    if (persistentAccount.getUserOid().equals(user.oid)) {
                        return persistentAccount;
                    }
                } else if (user.userId != 0) {
                    if (persistentAccount.getUserId() == user.userId) {
                        return persistentAccount;
                    }
                } else if (webFingerIdValid) {
                    if (persistentAccount.getWebFingerId().equals(user.getWebFingerId())) {
                        return persistentAccount;
                    }
                }
            }
        }
        return MyAccount.EMPTY;
    }

    public boolean hasMyUser(@NonNull Collection<MbUser> users) {
        for (MbUser user : users) {
            if (fromUser(user).isValid()) return true;
        }
        return false;
    }

    public boolean isMe(@NonNull MbUser user) {
      return fromUser(user).isValid();
    }

    public boolean isMyUserId(long userId) {
        return fromUserId(userId).isValid();
    }

    /** Doesn't take origin into account */
    @NonNull
    public MyAccount fromUser(@NonNull MbUser user) {
        MyAccount ma = fromUserId(user.userId);
        return ma.isValid() ? ma : fromWebFingerId(user.getWebFingerId());
    }

    /**
     * Get MyAccount by the UserId. 
     * Please note that a valid User may not have an Account (in AndStatus)
     * @return EMPTY account if was not found
     */
    @NonNull
    public MyAccount fromUserId(long userId) {
        if (userId == 0) return MyAccount.EMPTY;
        return mAccounts.stream().filter(myAccount -> myAccount.getUserId() == userId).findFirst()
                .orElse(MyAccount.EMPTY);
    }

    /** Doesn't take origin into account */
    @NonNull
    MyAccount fromWebFingerId(String webFingerId) {
        if (TextUtils.isEmpty(webFingerId)) return MyAccount.EMPTY;
        return mAccounts.stream().filter(myAccount -> myAccount.getWebFingerId().equals(webFingerId)).findFirst()
                .orElse(MyAccount.EMPTY);
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

    @NonNull
    public MyAccount getFirstSucceeded() {
        return getFirstSucceededForOriginId(0);
    }

    @NonNull
    public MyAccount getFirstSucceededForOrigin(@NonNull Origin origin) {
        return getFirstSucceededForOriginId(origin.getId());
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided originId.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param originId May be 0 to search in any Origin
     * @return Invalid account if not found
     */
    @NonNull
    public MyAccount getFirstSucceededForOriginId(long originId) {
        MyAccount ma = MyAccount.EMPTY;
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
            if (ma.shouldBeSyncedAutomatically()) return true;
        }
        return false;
    }

    /** @return 0 if no syncing is needed */
    public long minSyncIntervalMillis() {
        return mAccounts.stream()
                .filter(MyAccount::shouldBeSyncedAutomatically)
                .map(MyAccount::getEffectiveSyncFrequencyMillis)
                .min(Long::compareTo).orElse(0L);
    }

    /** Should not be called from UI thread
     * Find MyAccount, which may be linked to a message in this origin.
     * First try two supplied user IDs, then try any other existing account
     * @return Invalid account if nothing suitable found
     */
    @NonNull
    public MyAccount getAccountForThisMessage(long originId, MyAccount firstUser, MyAccount preferredUser,
                                              boolean succeededOnly)  {
        final String method = "getAccountForThisMessage";
        MyAccount ma = firstUser == null ? MyAccount.EMPTY : firstUser;
        if (!accountFits(ma, originId, succeededOnly)) {
            ma = betterFit(ma, preferredUser == null ? MyAccount.EMPTY : preferredUser, originId, succeededOnly);
        }
        if (!accountFits(ma, originId, succeededOnly)) {
            ma = betterFit(ma, getFirstSucceededForOriginId(originId), originId, succeededOnly);
        }
        if (!accountFits(ma, originId, false)) {
            ma = MyAccount.EMPTY;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; originId=" + originId
                    + "; user1=" + ma
                    + (ma.equals(preferredUser) ? "" : "; user2=" + preferredUser)
                    + (succeededOnly ? "; succeeded only" : ""));
        }
        return ma;
    }

    private boolean accountFits(MyAccount ma, long originId, boolean succeededOnly) {
        return ma != null
                && (succeededOnly ? ma.isValidAndSucceeded() : ma.isValid())
                && (originId == 0 || ma.getOriginId() == originId);
    }

    @NonNull
    private MyAccount betterFit(@NonNull MyAccount oldMa, @NonNull MyAccount newMa, long originId, boolean succeededOnly) {
        if (accountFits(oldMa, originId, succeededOnly) || !accountFits(newMa, originId, false)) {
            return oldMa;
        }
        if (!oldMa.isValid() && newMa.isValid()) {
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

    public List<MyAccount> accountsToSync() {
        boolean syncedAutomaticallyOnly = hasSyncedAutomatically();
        return list().stream().filter( myAccount -> accountToSyncFilter(myAccount, syncedAutomaticallyOnly))
                .collect(Collectors.toList());
    }

    private boolean accountToSyncFilter(MyAccount account, boolean syncedAutomaticallyOnly) {
        if ( !account.isValidAndSucceeded()) {
            MyLog.v(this, "Account '" + account.getAccountName() + "' skipped as invalid authenticated account");
            return false;
        }
        if (syncedAutomaticallyOnly && !account.isSyncedAutomatically()) {
            MyLog.v(this, "Account '" + account.getAccountName() + "' skipped as it is not synced automatically");
            return false;
        }
        return true;
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
        return "PersistentAccounts{" + mAccounts + '}';
    }

    @Override
    public int hashCode() {
        return mAccounts.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PersistentAccounts other = (PersistentAccounts) o;
        return mAccounts.equals(other.mAccounts);
    }

    public boolean isMeOrMyFriend(long userId) {
        return isMyUserId(userId) || isMyFriend(userId);
    }

    private boolean isMyFriend(long userId) {
        return myFriends.contains(userId);
    }

    private void initializeMyFriends() {
        myFriends.clear();
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) return;
        String sql = "SELECT DISTINCT " + FriendshipTable.FRIEND_ID + " FROM " + FriendshipTable.TABLE_NAME
            + " WHERE " + FriendshipTable.FOLLOWED + "=1" + " AND " + FriendshipTable.USER_ID
            + SqlUserIds.fromIds(mAccounts.stream().map(MyAccount::getUserId).collect(Collectors.toList())).getSql();
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                myFriends.add(cursor.getLong(0));
            }
        } catch (Exception e) {
            MyLog.i(this, "SQL:'" + sql + "'", e);
        }
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

    @NonNull
    public static SqlUserIds myAccountIds() {
        Context context = MyContextHolder.get().context();
        return SqlUserIds.fromIds(
            getAccounts(context).stream()
            .map(account -> AccountData.fromAndroidAccount(context, account).getDataLong(MyAccount.KEY_USER_ID, 0))
            .filter(id -> id > 0)
            .collect(Collectors.toList())
        );
    }

    @NonNull
    public static List<Account> getAccounts(Context context) {
        if (Permissions.checkPermission(context, Permissions.PermissionType.GET_ACCOUNTS) ) {
            AccountManager am = AccountManager.get(context);
            return Arrays.asList(am.getAccountsByType(AuthenticatorService.ANDROID_ACCOUNT_TYPE));
        }
        return Collections.emptyList();
    }
}
