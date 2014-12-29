package org.andstatus.app.account;

import android.accounts.AccountManager;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.Builder;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.backup.MyBackupDataInput;
import org.andstatus.app.backup.MyBackupDataOutput;
import org.andstatus.app.backup.MyBackupDescriptor;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;
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
    
    private Map<String,MyAccount> mAccounts = new ConcurrentHashMap<String, MyAccount>();
    private int distinctOriginsCount = 0;
    
    private PersistentAccounts() {
    }
    
    /**
     * Get list of all persistent accounts
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link #getCredentialsVerified()} is the main differentiator.
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

    public String getDefaultAccountName() {
        return MyPreferences.getDefaultSharedPreferences().getString(KEY_DEFAULT_ACCOUNT_NAME, "");
    }

    public int getDistinctOriginsCount() {
        return distinctOriginsCount;
    }
    
    private void calculateDistinctOriginsCount() {
        Set<Long> originIds = new HashSet<Long>();
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
            MyAccount.Builder.fromMyAccount(MyContextHolder.get(), ma, "delete").deleteData();

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
     * @return null if was not found
     */
    public MyAccount fromAccountName(String accountNameIn) {
        MyAccount myAccount = null;
        AccountName accountName = AccountName.fromAccountName(MyContextHolder.get(), accountNameIn);
        if (TextUtils.isEmpty(accountName.getUsername())) {
            return myAccount;
        }

        for (MyAccount persistentAccount : mAccounts.values()) {
            if (persistentAccount.getAccountName().compareTo(accountName.toString()) == 0) {
                myAccount = persistentAccount;
                break;
            }
        }
        // Try to find persisted Account which was not loaded yet
        if (myAccount == null
                && !TextUtils.isEmpty(accountName.toString())) {
            android.accounts.Account[] androidAccounts = AccountManager.get(
                    MyContextHolder.get().context()).getAccountsByType(
                    AuthenticatorService.ANDROID_ACCOUNT_TYPE);
            for (android.accounts.Account androidAccount : androidAccounts) {
                if (accountName.compareToString(androidAccount.name) == 0) {
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
     * @return MyAccount or null if no persistent accounts exist
     */
    public MyAccount getCurrentAccount() {
        MyAccount ma = fromAccountName(currentAccountName);
        if (ma != null) {
            return ma;
        }
        currentAccountName = "";
        ma = fromAccountName(defaultAccountName);
        if (ma == null) {
            defaultAccountName = "";
        }
        if (ma == null && !mAccounts.isEmpty()) {
            ma = mAccounts.values().iterator().next();
        }
        if (ma != null) {
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
     * 
     * @param Context
     * @return Account name or empty string if no persistent accounts exist
     */
    public String getCurrentAccountName() {
        MyAccount ma = getCurrentAccount();
        if (ma != null) {
            return ma.getAccountName();
        } else {
            return "";
        }
    }
    
    /**
     * @return 0 if no current account
     */
    public long getCurrentAccountUserId() {
        MyAccount ma = getCurrentAccount();
        if (ma != null) {
            return ma.getUserId();
        }
        return 0;
    }

    public boolean isAccountUseId(long userId) {
        return fromUserId(userId) != null;
    }

    /**
     * Get MyAccount by the UserId. Valid User may not have an Account (in AndStatus)
     * @param userId
     * @return null if not found
     */
    public MyAccount fromUserId(long userId) {
        MyAccount ma = null;
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
     * @param originId
     * @return null if not found
     */
    public MyAccount findFirstMyAccountByOriginId(long originId) {
        MyAccount ma = null;
        for (MyAccount persistentAccount : mAccounts.values()) {
            if (persistentAccount.getOriginId() == originId) {
                if ( persistentAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    ma = persistentAccount;
                    break;
                }
                if (ma == null) {
                    ma = persistentAccount;
                }
            }
        }
        return ma;
    }
    
    /**
     * Find account of the User linked to this message, 
     * or other appropriate account in a case the User is not an Account.
     * For any action with the message we should choose an Account 
     * from the same originating (source) System.
     * @param messageId  Message ID
     * @param userIdForThisMessage The message is in his timeline. 0 if the message doesn't belong to any timeline
     * @param preferredOtherUserId Preferred account (or 0), used in a case userId is not an Account 
     *          or is not linked to the message 
     * @return null if nothing suitable found
     */
    public MyAccount getAccountWhichMayBeLinkedToThisMessage(long messageId, long userIdForThisMessage, 
            long preferredOtherUserId)  {
        final String method = "getAccountWhichMayBeLinkedToThisMessage";
        MyAccount ma = fromUserId(userIdForThisMessage);
        if ((messageId == 0) || (ma == null)) {
            ma = fromUserId(preferredOtherUserId);
        }
        long originId = MyProvider.msgIdToOriginId(messageId);
        if ((ma == null) || (originId != ma.getOriginId())) {
           ma = findFirstMyAccountByOriginId(originId); 
        }
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method + "; msgId=" + messageId +"; userId=" + userIdForThisMessage 
                    + " -> account=" + (ma==null ? "null" : ma.getAccountName()));
        }
        return ma;
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
        MyPreferences.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
    }
    
    public void onMyPreferencesChanged(MyContext myContext) {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount ma : mAccounts.values()) {
            Builder builder = Builder.fromMyAccount(myContext, ma, "onMyPreferencesChanged");
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
        } else if (ma != null) {
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
        MyLog.i(this, method + "; started, " + data.getDataSize() + " bytes");
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
}
