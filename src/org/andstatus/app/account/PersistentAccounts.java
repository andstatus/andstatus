package org.andstatus.app.account;

import android.accounts.AccountManager;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.Builder;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;

import java.util.Collection;
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
    
    private ConcurrentHashMap<String,MyAccount> persistentAccounts = new ConcurrentHashMap<String, MyAccount>();
    
    private PersistentAccounts() {
    }
    
    /**
     * Get list of all persistent accounts
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link #getCredentialsVerified()} is the main differentiator.
     * 
     * @param context
     * @return not null 
     */
    public Collection<MyAccount> collection() {
        return persistentAccounts.values();
    }
    
    public int size() {
        return persistentAccounts.size();
    }

    public PersistentAccounts initialize() {
        return initialize(MyContextHolder.get());
    }
    
    public PersistentAccounts initialize(MyContext myContext) {
        defaultAccountName = MyPreferences.getDefaultSharedPreferences().getString(KEY_DEFAULT_ACCOUNT_NAME, "");
        persistentAccounts.clear();
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account account : aa) {
            MyAccount ma = Builder.fromAndroidAccount(myContext, account).getAccount();
            if (ma.isValid()) {
                persistentAccounts.put(ma.getAccountName(), ma);
            } else {
                MyLog.e(this, "The account is not valid: " + ma);
            }
        }
        MyLog.v(this, "Account list initialized, " + persistentAccounts.size() + " accounts");
        return this;
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
        for (MyAccount persistentAccount : persistentAccounts.values()) {
            if (persistentAccount.equals(ma)) {
                found = true;
                break;
            }
        }
        if (found) {
            MyAccount.Builder.fromMyAccount(ma).deleteData();

            // And delete the object from the list
            persistentAccounts.remove(ma.getAccountName());

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

        for (MyAccount persistentAccount : persistentAccounts.values()) {
            if (persistentAccount.getAccountName().compareTo(accountName.toString()) == 0) {
                myAccount = persistentAccount;
                break;
            }
        }
        if (myAccount == null) {
            // Try to find persisted Account which was not loaded yet
            if (!TextUtils.isEmpty(accountName.toString())) {
                android.accounts.Account[] androidAccounts = AccountManager.get(
                        MyContextHolder.get().context()).getAccountsByType(
                        AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                for (android.accounts.Account androidAccount : androidAccounts) {
                    if (accountName.compareTo(androidAccount.name) == 0) {
                        myAccount = Builder.fromAndroidAccount(MyContextHolder.get(), androidAccount).getAccount();
                        persistentAccounts.put(myAccount.getAccountName(), myAccount);
                        MyPreferences.onPreferencesChanged();
                        break;
                    }
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
        if (ma == null) {
            if (!persistentAccounts.isEmpty()) {
                ma = persistentAccounts.values().iterator().next();
            }
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


    /**
     * Get MyAccount by the UserId. Valid User may not have an Account (in AndStatus)
     * @param userId
     * @return null if not found
     */
    public MyAccount fromUserId(long userId) {
        MyAccount ma = null;
        for (MyAccount persistentAccount : persistentAccounts.values()) {
            if (persistentAccount.getUserId() == userId) {
                ma = persistentAccount;
                break;
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
        for (MyAccount persistentAccount : persistentAccounts.values()) {
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
        MyAccount ma = fromUserId(userIdForThisMessage);
        if (messageId == 0 || ma == null) {
            ma = fromUserId(preferredOtherUserId);
        }
        long originId = MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.ORIGIN_ID, messageId);
        if (ma == null || originId != ma.getOriginId()) {
           ma = findFirstMyAccountByOriginId(originId); 
        }
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "getMyAccountLinkedToThisMessage msgId=" + messageId +"; userId=" + userIdForThisMessage 
                    + " -> account=" + (ma==null ? "null" : ma.getAccountName()));
        }
        return ma;
    }

    /**
     * Set provided MyAccount as Current one.
     * Current account selection is not persistent
     */
    public void setCurrentAccount(MyAccount ma) {
        if (ma != null) {
            currentAccountName = ma.getAccountName();
        }
    }

    /**
     * Set provided MyAccount as a default one.
     * Default account selection is persistent
     */
    private void setDefaultAccount(MyAccount ma) {
        if (ma != null) {
            defaultAccountName = ma.getAccountName();
        }
        MyPreferences.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
    }
    
    public void onMyPreferencesChanged() {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount ma : persistentAccounts.values()) {
            Builder builder = Builder.fromMyAccount(ma);
            builder.setSyncFrequency(syncFrequencySeconds);
            builder.save();
        }
    }
}
