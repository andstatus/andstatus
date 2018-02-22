package org.andstatus.app.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
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
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class MyAccounts {
    /**
     * Name of "current" account: it is not stored when application is killed
     */
    private volatile String currentAccountName = "";

    private final MyContext myContext;
    private final Set<MyAccount> myAccounts = new ConcurrentSkipListSet<>();
    private int distinctOriginsCount = 0;

    private MyAccounts(MyContext myContext) {
        this.myContext = myContext;
    }

    @NonNull
    public Set<MyAccount> get() {
        return myAccounts;
    }
    
    public boolean isEmpty() {
        return myAccounts.isEmpty();
    }
    
    public int size() {
        return myAccounts.size();
    }
    
    public MyAccounts initialize() {
        this.myAccounts.clear();
        for (android.accounts.Account account : getAccounts(myContext.context())) {
            MyAccount ma = Builder.fromAndroidAccount(myContext, account).getAccount();
            if (ma.isValid()) {
                myAccounts.add(ma);
            } else {
                MyLog.e(this, "The account is invalid: " + ma);
            }
        }
        calculateDistinctOriginsCount();
        MyLog.v(this, "Accounts initialized, " + this.myAccounts.size() + " accounts in " + distinctOriginsCount + " origins");
        return this;
    }

    public MyAccount getDefaultAccount() {
        return myAccounts.isEmpty() ? MyAccount.EMPTY : myAccounts.iterator().next();
    }

    public int getDistinctOriginsCount() {
        return distinctOriginsCount;
    }
    
    private void calculateDistinctOriginsCount() {
        Set<Origin> origins = new HashSet<>();
        for (MyAccount ma : myAccounts) {
            origins.add(ma.getOrigin());
        }
        distinctOriginsCount = origins.size();
    }
    
    public static MyAccounts newEmpty(MyContext myContext) {
        return new MyAccounts(myContext);
    }
    
    /**
     * @return Was the MyAccount (and Account) deleted?
     */
    public boolean delete(MyAccount ma) {
        MyAccount toDelete = myAccounts.stream().filter(myAccount -> myAccount.equals(ma))
                .findFirst().orElse(MyAccount.EMPTY);
        if (!toDelete.isValid()) return false;

        MyAccount.Builder.fromMyAccount(myContext, toDelete, "delete", false).deleteData();
        myAccounts.remove(toDelete);
        MyPreferences.onPreferencesChanged();
        return true;
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
        if (!accountName.isValid()) return MyAccount.EMPTY;

        for (MyAccount persistentAccount : myAccounts) {
            if (persistentAccount.getAccountName().equals(accountName.toString())) {
                return persistentAccount;
            }
        }
        for (android.accounts.Account androidAccount : getAccounts(myContext.context())) {
            if (accountName.toString().equals(androidAccount.name)) {
                MyAccount myAccount = Builder.fromAndroidAccount(myContext, androidAccount).getAccount();
                myAccounts.add(myAccount);
                MyPreferences.onPreferencesChanged();
                return myAccount;
            }
        }
        return MyAccount.EMPTY;
    }

    @NonNull
    public MyAccount fromActorOfSameOrigin(@NonNull Actor actor) {
        return fromActor(actor, true);
    }

    /** Doesn't take origin into account */
    @NonNull
    public MyAccount fromActor(@NonNull Actor actor) {
        return fromActor(actor, false);
    }

    @NonNull
    public MyAccount fromActor(@NonNull Actor other, boolean sameOriginOnly) {
        return myAccounts.stream().filter(ma -> ma.getActor().isSame(other, sameOriginOnly))
                .findFirst().orElse(MyAccount.EMPTY);
    }

    /**
     * Get MyAccount by the ActorId.
     * Please note that a valid Actor may not have an Account (in AndStatus)
     * @return EMPTY account if was not found
     */
    @NonNull
    public MyAccount fromActorId(long actorId) {
        if (actorId == 0) return MyAccount.EMPTY;
        return myAccounts.stream().filter(ma -> ma.getActorId() == actorId).findFirst()
                .orElseGet(() -> myAccounts.stream()
                        .filter(ma -> myContext.users().fromActorId(actorId).actorIds.contains(ma.getActorId()))
                        .findFirst().orElseGet(() -> fromFriendsActorId(actorId)));
    }

    private MyAccount fromFriendsActorId(long actorId) {
        return myAccounts.stream().filter(ma -> myContext.users().myFriends.getOrDefault(actorId, Actor.EMPTY)
                    .user.actorIds.contains(ma.getActorId()))
                .findFirst().orElse(MyAccount.EMPTY);
    }

    /** Doesn't take origin into account */
    @NonNull
    public MyAccount fromWebFingerId(String webFingerId) {
        if (TextUtils.isEmpty(webFingerId)) return MyAccount.EMPTY;
        return myAccounts.stream().filter(myAccount -> myAccount.getWebFingerId().equals(webFingerId)).findFirst()
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
        if (ma.isValid()) return ma;

        currentAccountName = "";
        ma = getDefaultAccount();
        if (!ma.isValid()) {
            for (MyAccount myAccount : myAccounts) {
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
    public long getCurrentAccountActorId() {
        return getCurrentAccount().getActorId();
    }

    @NonNull
    public MyAccount getFirstSucceeded() {
        return getFirstSucceededForOrigin(Origin.EMPTY);
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided originId.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param origin May be EMPTY to search in any Origin
     * @return Invalid account if not found
     */
    @NonNull
    public MyAccount getFirstSucceededForOrigin(@NonNull Origin origin) {
        MyAccount ma = MyAccount.EMPTY;
        for (MyAccount myAccount : myAccounts) {
            if (!origin.isValid() || myAccount.getOrigin().equals(origin)) {
                if (!ma.isValid()) {
                    ma = myAccount;
                }
                if (myAccount.isValidAndSucceeded()) {
                    if (!ma.isValidAndSucceeded()) {
                        ma = myAccount;
                    }
                    if (myAccount.isSyncedAutomatically()) {
                        ma = myAccount;
                        break;
                    }
                }
            }
        }
        return ma;
    }

    public boolean hasSyncedAutomatically() {
        for (MyAccount ma : myAccounts) {
            if (ma.shouldBeSyncedAutomatically()) return true;
        }
        return false;
    }

    /** @return 0 if no syncing is needed */
    public long minSyncIntervalMillis() {
        return myAccounts.stream()
                .filter(MyAccount::shouldBeSyncedAutomatically)
                .map(MyAccount::getEffectiveSyncFrequencyMillis)
                .min(Long::compareTo).orElse(0L);
    }

    /** Should not be called from UI thread
     * Find MyAccount, which may be linked to a note in this origin.
     * First try two supplied accounts, then try any other existing account
     */
    @NonNull
    public MyAccount getAccountForThisNote(Origin origin, MyAccount firstAccount, MyAccount preferredAccount,
                                           boolean succeededOnly)  {
        final String method = "getAccountForThisNote";
        MyAccount ma = firstAccount == null ? MyAccount.EMPTY : firstAccount;
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, preferredAccount == null ? MyAccount.EMPTY : preferredAccount, origin, succeededOnly);
        }
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, getFirstSucceededForOrigin(origin), origin, succeededOnly);
        }
        if (!accountFits(ma, origin, false)) {
            ma = MyAccount.EMPTY;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; origin=" + origin.getName()
                    + "; account1=" + ma
                    + (ma.equals(preferredAccount) ? "" : "; account2=" + preferredAccount)
                    + (succeededOnly ? "; succeeded only" : ""));
        }
        return ma;
    }

    private boolean accountFits(MyAccount ma, @NonNull Origin origin, boolean succeededOnly) {
        return ma != null
                && (succeededOnly ? ma.isValidAndSucceeded() : ma.isValid())
                && (!origin.isValid() || ma.getOrigin().equals(origin));
    }

    @NonNull
    private MyAccount betterFit(@NonNull MyAccount oldMa, @NonNull MyAccount newMa, @NonNull Origin origin,
                                boolean succeededOnly) {
        if (accountFits(oldMa, origin, succeededOnly) || !accountFits(newMa, origin, false)) {
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
        for (MyAccount ma : myAccounts) {
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
        return myAccounts.stream().filter( myAccount -> accountToSyncFilter(myAccount, syncedAutomaticallyOnly))
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
            for (MyAccount ma : myAccounts) {
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
        return "PersistentAccounts{" + myAccounts + '}';
    }

    @Override
    public int hashCode() {
        return myAccounts.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MyAccounts other = (MyAccounts) o;
        return myAccounts.equals(other.myAccounts);
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
            MyPreferences.onPreferencesChanged();
        }
    }

    @NonNull
    public static SqlActorIds myAccountIds() {
        Context context = MyContextHolder.get().context();
        return SqlActorIds.fromIds(
            getAccounts(context).stream()
            .map(account -> AccountData.fromAndroidAccount(context, account).getDataLong(MyAccount.KEY_ACTOR_ID, 0))
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

    void addIfNew(@NonNull MyAccount myAccount) {
        if (!myAccounts.contains(myAccount)) myAccounts.add(myAccount);
    }
}
