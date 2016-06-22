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

package org.andstatus.app.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.DatabaseConverterController;
import org.andstatus.app.database.OriginTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.OAuthConsumerAndProvider;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.MbConfig;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable class that holds "AndStatus account"-specific information including: 
 * a Microblogging System (twitter.com, identi.ca etc.), 
 * Username in that system and {@link Connection} to it.
 * 
 * @author yvolk@yurivolkov.com
 */
public final class MyAccount {
    private static final String TAG = MyAccount.class.getSimpleName();

    //------------------------------------------------------------
    // Key names for MyAccount preferences are below:
    
    /**
     * The Key for the android.accounts.Account bundle;
     */
    public static final String KEY_ACCOUNT = "account";
    /**
     * Is the MyAccount persistent in AccountManager;
     */
    public static final String KEY_PERSISTENT = "persistent";

    /**
     * This Key is both global for the application and the same - for one MyAccount
     * Global: Username of currently selected MyAccount (Current MyAccount)
     * This MyAccount: Username of the {@link UserTable} corresponding to this {@link MyAccount}
     */
    public static final String KEY_USERNAME = "username";
    /**
     * New Username typed / selected in UI
     * It doesn't immediately change "Current MyAccount"
     */
    public static final String KEY_USERNAME_NEW = "username_new";
    /**
     * {@link UserTable#_ID} in our System.
     */
    public static final String KEY_USER_ID = "user_id";
    /**
     * {@link UserTable#USER_OID} in Microblogging System.
     */
    public static final String KEY_USER_OID = "user_oid";

    /**
     * Is OAuth on for this MyAccount?
     */
    public static final String KEY_OAUTH = "oauth";

    public static final String KEY_IS_DEFAULT_ACCOUNT = "is_default_account";
    
    /**
     * Storing version of the account data
     */
    public static final String KEY_VERSION = "myversion";

    /**
     * This account is in the process of deletion and should be ignored...
     */
    public static final String KEY_DELETED = "deleted";
    
    /** @see {@link ContentResolver#getIsSyncable(Account, String)} */
    public static final String KEY_IS_SYNCABLE = "is_syncable";

    /** This corresponds to turning syncing on/off in Android Accounts
     * @see {@link ContentResolver#getSyncAutomatically(Account, String)} */
    public static final String KEY_SYNC_AUTOMATICALLY = "sync_automatically";
    
    /** Companion class used to load/create/change/delete {@link MyAccount}'s data */
    public static final class Builder implements Parcelable {
        private static final String TAG = MyAccount.TAG + "." + Builder.class.getSimpleName();

        private MyContext myContext;
        private final MyAccount myAccount;
        
        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        public static Builder newOrExistingFromAccountName(MyContext myContext, String accountName, TriState isOAuthTriState) {
            MyAccount persistentAccount = myContext.persistentAccounts().fromAccountName(accountName);
            if (persistentAccount.isValid()) {
                return fromMyAccount(myContext, persistentAccount, "newOrExistingFromAccountName", false);
            } else {
                return newFromAccountName(myContext, accountName, isOAuthTriState);
            }
        }
        
        /**
         * Creates new account, which is not Persistent yet
         * @param accountName
         */
        private static Builder newFromAccountName(MyContext myContext, String accountName, TriState isOAuthTriState) {
            MyAccount ma = new MyAccount(myContext, null);
            ma.oAccountName = AccountName.fromAccountName(myContext, accountName);
            ma.setOAuth(isOAuthTriState);
            return fromMyAccount(myContext, ma, "newFromAccountName", true);
        }

        private static MyAccount getEmptyAccount(MyContext myContext, String accountName) {
            return newFromAccountName(myContext, accountName, TriState.UNKNOWN).getAccount();
        }
        
        /**
         * Loads existing account from Persistence 
         * @param myContext 
         * @param account should not be null
         */
        protected static Builder fromAndroidAccount(MyContext myContext, android.accounts.Account account) {
            return fromAccountData(myContext, AccountData.fromAndroidAccount(myContext, account), "fromAndroidAccount");
        }

        public static Builder fromJson(MyContext myContext, JSONObject jso) throws JSONException {
            return fromAccountData(myContext, AccountData.fromJson(jso, false), "fromJson");
        }

        static Builder fromAccountData(MyContext myContext, AccountData accountData, String method) {
            return fromMyAccount(myContext, new MyAccount(myContext, accountData), method, false);
        }
        
        protected static Builder fromMyAccount(MyContext myContext, MyAccount ma, String method, boolean isNew) {
            Builder builder = new Builder(myContext, ma);
            builder.setConnection();
            builder.fixInconsistenciesWithChangedEnvironmentSilently();
            if (!isNew) {
                builder.logLoadResult(method);
            }
            return builder;
        }

        private Builder(MyContext myContext, MyAccount myAccount) {
            this.myContext = myContext;
            this.myAccount = myAccount;
        }

        private void setConnection() {
            Origin origin = myAccount.oAccountName.getOrigin();
            OriginConnectionData connectionData = origin.getConnectionData(TriState.fromBoolean(myAccount.isOAuth));
            connectionData.setAccountUserOid(myAccount.userOid);
            connectionData.setAccountUsername(myAccount.getUsername());
            connectionData.setDataReader(myAccount.accountData);
            try {
                myAccount.connection = connectionData.getConnectionClass().newInstance();
                myAccount.connection.enrichConnectionData(connectionData);
                myAccount.connection.setAccountData(connectionData);
            // TODO: Since API19 we will use ReflectiveOperationException as a common superclass of these two exceptions: InstantiationException and IllegalAccessException
            } catch (InstantiationException e) {
                myAccount.connection = null;
                MyLog.i(TAG, e);
            } catch (IllegalAccessException e) {
                myAccount.connection = null;
                MyLog.i(TAG, e);
            }
        }
        
        private void fixInconsistenciesWithChangedEnvironmentSilently() {
            if (myAccount.version != MyAccount.ACCOUNT_VERSION) {
                return;
            }
            boolean changed = false;
            if (isPersistent() && myAccount.userId==0) {
                changed = true;
                assignUserId();
                MyLog.e(TAG, "MyAccount '" + myAccount.getAccountName() + "' was not connected to the User table. UserId=" + myAccount.userId);
            }
            if (myAccount.syncFrequencySeconds == 0) {
                changed = true;
            }
            if (!myAccount.getCredentialsPresent()
                    && myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                MyLog.e(TAG, "User's credentials were lost?! Fixing...");
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                changed = true;
            }
            if (changed && isPersistent()) {
                saveSilently();
            }
        }

        private void logLoadResult(String method) {
            if (myAccount.isValid()) { 
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(TAG, method + " Loaded " + this.toString());
                }
            } else {
                MyLog.i(TAG, method + " Failed to load: Invalid account; version=" + myAccount.version + "; " + this.toString());
            }
        }
        
        public MyAccount getAccount() {
            return myAccount;
        }

        /**
         * @return Is this object persistent 
         */
        public boolean isPersistent() {
            return myAccount.isPersistent();
        }

        /**
         * Delete all User's data
         * @return true = success 
         */
        boolean deleteData() {
            boolean ok = true;

            // Old preferences file may be deleted, if it exists...
            ok = SharedPreferencesUtil.delete(myContext.context(), myAccount.oAccountName.prefsFilename());

            if (isPersistent() && myAccount.userId != 0) {
                // TODO: Delete databases for this User
                myAccount.userId = 0;
            }
            setAndroidAccountDeleted();
            return ok;
        }
        
        private void setAndroidAccountDeleted() {
            myAccount.accountData.setDataBoolean(KEY_DELETED, true);
        }

        static class SaveResult {
            boolean success = false;
            boolean changed = false;
            boolean savedToAccountManager = false;
        }
        
        void save() {
            if (saveSilently().savedToAccountManager && myContext.isReady()) {
                MyPreferences.onPreferencesChanged();
            }
        }

        /**
         * Save this MyAccount to AccountManager
         */
        SaveResult saveSilently() {
            SaveResult result = new SaveResult();
            try {
                if (!myAccount.isValid()) {
                    MyLog.v(TAG, "Didn't save invalid account: " + myAccount);
                    return result;
                }
                Account androidAccount = getNewOrExistingAndroidAccount();
                myAccount.accountData.setDataString(KEY_USERNAME, myAccount.oAccountName.getUsername());
                myAccount.accountData.setDataString(KEY_USER_OID, myAccount.userOid);
                myAccount.accountData.setDataString(Origin.KEY_ORIGIN_NAME, myAccount.oAccountName.getOriginName());
                myAccount.credentialsVerified.put(myAccount.accountData);
                myAccount.accountData.setDataBoolean(KEY_OAUTH, myAccount.isOAuth);
                myAccount.accountData.setDataLong(KEY_USER_ID, myAccount.userId);
                if (myAccount.connection != null) {
                    myAccount.connection.save(myAccount.accountData);
                }
                myAccount.accountData.setPersistent(true);
                myAccount.accountData.setDataBoolean(MyAccount.KEY_IS_SYNCABLE, myAccount.mIsSyncable);
                myAccount.accountData.setDataBoolean(MyAccount.KEY_SYNC_AUTOMATICALLY, myAccount.mSyncAutomatically);
                if (myAccount.syncFrequencySeconds == 0) {
                    setSyncFrequency(MyPreferences.getSyncFrequencySeconds());
                }
                myAccount.accountData.setDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, myAccount.syncFrequencySeconds);
                myAccount.accountData.setDataInt(KEY_VERSION, myAccount.version);
                if (androidAccount != null) {
                    myAccount.accountData.saveDataToAccount(myContext, androidAccount, result);
                }
                MyLog.v(this, (result.savedToAccountManager ? " Saved " 
                        : ( result.changed ? " Didn't save?! " : " Didn't change") ) + this.toString());
                if (result.savedToAccountManager && result.changed) {
                    myContext.persistentTimelines().addDefaultMyAccountTimelinesIfNoneFound(myAccount);
                }
            } catch (Exception e) {
                MyLog.e(this, "Saving " + myAccount.getAccountName(), e);
            }
            return result;
        }

        private Account getNewOrExistingAndroidAccount() {
            Account androidAccount = myAccount.getExisingAndroidAccount();
            if ((androidAccount == null) && myAccount.isValidAndSucceeded()) {
                try {
                    /**
                     * Now add this account to the Account Manager See {@link
                     * com.android.email.provider.EmailProvider.
                     * createAccountManagerAccount(Context, String, String)}
                     * Note: We could add userdata from {@link userData} Bundle,
                     * but we decided to add it below one by one item
                     */
                    androidAccount = new android.accounts.Account(myAccount.getAccountName(),
                            AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                    android.accounts.AccountManager am = AccountManager.get(
                            myContext.context());
                    am.addAccountExplicitly(androidAccount, myAccount.getPassword(), null);

                    // Without SyncAdapter we got the error:
                    // SyncManager(865): can't find a sync adapter for SyncAdapterType Key
                    // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app}, 
                    // removing settings for it

                    MyLog.v(TAG, "Persisted " + myAccount.getAccountName());
                } catch (Exception e) {
                    MyLog.e(TAG, "Adding Account to AccountManager", e);
                    androidAccount = null;
                }
            }
            return androidAccount;
        }
        
        public boolean getOriginConfig() throws ConnectionException {
            boolean ok = false;
            if (!ok) {
                MbConfig config = null;
                try {
                    config = myAccount.getConnection().getConfig();
                    ok = (!config.isEmpty());
                    if (ok) {
                        Origin.Builder originBuilder = new Origin.Builder(
                                myContext.persistentOrigins().fromId(myAccount.getOriginId())); 
                        originBuilder.save(config);
                    }
                } finally {
                    MyLog.v(this, "Get Origin config " + (ok ? "succeeded" : "failed"));
                }
            }
            return ok;
        }
        
        /**
         * Verify the user's credentials. Returns true if authentication was
         * successful
         * 
         * @see CredentialsVerificationStatus
         * @param reVerify Verify even if it was verified already
         * @return boolean
         */
        public boolean verifyCredentials(boolean reVerify) throws ConnectionException {
            boolean ok = false;
            if (!reVerify && myAccount.isValidAndSucceeded()) {
                ok = true;
            }
            if (!ok) {
                try {
                    MbUser user = myAccount.getConnection().verifyCredentials();
                    ok = onCredentialsVerified(user, null);
                } catch (ConnectionException e) {
                    ok = onCredentialsVerified(null, e);
                }
            }
            return ok;
        }
        
        public boolean onCredentialsVerified(MbUser user, ConnectionException ce) throws ConnectionException {
            boolean ok = (ce == null) && user != null && !user.isEmpty();
            if (ok && TextUtils.isEmpty(user.oid)) {
                ok = false;
            }
            String newName = "";
            boolean errorSettingUsername = false;
            if (ok) {
                newName = user.getUserName();
                Origin origin = myContext.persistentOrigins().fromId(user.originId);
                ok = origin.isUsernameValid(newName);
                errorSettingUsername = !ok;
            }
            boolean credentialsOfOtherUser = false;
            // We are comparing user names ignoring case, but we fix correct case
            // as the Originating system tells us. 
            if (ok  && !TextUtils.isEmpty(myAccount.getUsername()) 
                    && myAccount.getUsername().compareToIgnoreCase(newName) != 0) {
                // Credentials belong to other User ??
                ok = false;
                credentialsOfOtherUser = true;
            }
            if (ok) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
                myAccount.userOid = user.oid;
                if (DatabaseConverterController.isUpgrading()) {
                    MyLog.v(TAG, "Upgrade in progress");
                    myAccount.userId = myAccount.accountData.getDataLong(KEY_USER_ID, myAccount.userId);
                } else {
                    DataInserter di = new DataInserter(myAccount);
                    myAccount.userId = di.insertOrUpdateUser(user);
                }
            }
            if (ok && !isPersistent()) {
                // Now we know the name (or proper case of the name) of this User!
                // We don't recreate MyAccount object for the new name
                //   in order to preserve credentials.
                myAccount.oAccountName = AccountName.fromOriginAndUserNames(
                        myContext,
                        myAccount.oAccountName.getOriginName(), newName);
                myAccount.connection.save(myAccount.accountData);
                setConnection();
                save();
            }
            if (!ok || !myAccount.getCredentialsPresent()) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
            }
            save();

            if (ce != null) {
                throw ce;
            }
            if (credentialsOfOtherUser) {
                MyLog.e(TAG, myContext.context().getText(R.string.error_credentials_of_other_user) + ": "
                        + newName);
                throw new ConnectionException(StatusCode.CREDENTIALS_OF_OTHER_USER, newName);
            }
            if (errorSettingUsername) {
                String msg = myContext.context().getText(R.string.error_set_username) + newName;
                MyLog.e(TAG, msg);
                throw new ConnectionException(StatusCode.AUTHENTICATION_ERROR, msg);
            }
            return ok;
        }
        
        public void setUserTokenWithSecret(String token, String secret) {
            myAccount.getConnection().setUserTokenWithSecret(token, secret);
        }

        public void setCredentialsVerificationStatus(CredentialsVerificationStatus cv) {
            myAccount.credentialsVerified = cv;
            if (cv != CredentialsVerificationStatus.SUCCEEDED 
                    && myAccount.connection != null) {
                myAccount.connection.clearAuthInformation();
            }
        }

        public void registerClient() throws ConnectionException {
            MyLog.v(TAG, "Registering client application for " + myAccount.getUsername());
            setConnection();
            myAccount.connection.registerClientForAccount();
        }

        /**
         * Password was moved to the connection object because it is needed there
         * 
         * @param password
         */
        public void setPassword(String password) {
            if (password.compareTo(myAccount.getConnection().getPassword()) != 0) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                myAccount.getConnection().setPassword(password);
            }
        }
        
        private void assignUserId() {
            myAccount.userId = MyQuery.userNameToId(myAccount.getOriginId(), myAccount.getUsername());
            if (myAccount.userId == 0) {
                DataInserter di = new DataInserter(myAccount);
                try {
                    // Construct "User" from available account info
                    // We need this User in order to be able to link Messages to him
                    MbUser mbUser = MbUser.fromOriginAndUserOid(myAccount.getOriginId(), myAccount.userOid);
                    mbUser.setUserName(myAccount.getUsername());
                    LatestUserMessages lum = new LatestUserMessages();
                    myAccount.userId = di.insertOrUpdateUser(mbUser, lum);
                    lum.save();
                } catch (Exception e) {
                    MyLog.e(TAG, "Construct user", e);
                }
            }
        }
        
        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            save();
            dest.writeParcelable(myAccount.accountData, flags);
        }
        
        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @Override
            public Builder createFromParcel(Parcel source) {
                return fromAccountData(MyContextHolder.get(), AccountData.fromBundle(source.readBundle()), "createFromParcel");
            }

            @Override
            public Builder[] newArray(int size) {
                return new Builder[size];
            }
        };
        
        @Override
        public String toString() {
            return myAccount.toString();
        }

        public void clearClientKeys() {
            myAccount.connection.clearClientKeys();
        }

        protected int getVersion() {
            return myAccount.version;
        }

        void setSyncFrequency(long syncFrequencySeconds) {
            myAccount.syncFrequencySeconds = syncFrequencySeconds;
        }
        
        /**
         * @return true if the old Android Account should be deleted
         */
        public boolean onOriginNameChanged(String originNameNew) {
            if (myAccount.oAccountName.getOriginName().equals(originNameNew) 
                    || TextUtils.isEmpty(originNameNew)) {
                return false;
            }
            MyLog.i(TAG, "renaming origin of " + myAccount.getAccountName() + " to " + originNameNew );
            setAndroidAccountDeleted();
            myAccount.oAccountName = AccountName.fromOriginAndUserNames(
                    myContext,
                    originNameNew,
                    myAccount.oAccountName.getUsername());
            saveSilently();
            return true;
        }
    }
    
    private final AccountData accountData;
    private AccountName oAccountName;
    private String userOid;
    /** Id in the database, see {@link UserTable#_ID} */
    private long userId;
    private Connection connection = null;
    /** Was this user authenticated last time _current_ credentials were verified?
     *  CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private CredentialsVerificationStatus credentialsVerified = CredentialsVerificationStatus.NEVER;
    /** Is this user authenticated with OAuth? */
    private boolean isOAuth = true;
    private long syncFrequencySeconds;
    private boolean mIsSyncable = true;
    private boolean mSyncAutomatically = true;
    private final int version;
    public static final int ACCOUNT_VERSION = 16;
    private boolean deleted;
    
    public enum CredentialsVerificationStatus {
        /** 
         * NEVER - means that User was never successfully authenticated with current credentials.
         *  This is why we reset the state to NEVER every time credentials have been changed.
         */
        NEVER(1), 
        FAILED(2),
        /** The User was successfully authenticated */
        SUCCEEDED(3);

        private int id;
        
        static final String KEY = "credentials_verified";
        
        private CredentialsVerificationStatus(int id) {
            this.id = id;
        }

        public void put(AccountDataWriter dw) {
            dw.setDataInt(KEY, id);
        }
        
        public void put(JSONObject jso) throws JSONException {
            jso.put(KEY, id);
        }
        
        public static CredentialsVerificationStatus load(SharedPreferences sp) {
            int id = sp.getInt(KEY, NEVER.id);
            return fromId(id);
        }

        public static CredentialsVerificationStatus fromId( long id) {
            CredentialsVerificationStatus status = NEVER;
            for(CredentialsVerificationStatus status1 : values()) {
                if (status1.id == id) {
                    status = status1;
                    break;
                }
            }
            return status;
        }
        
        public static CredentialsVerificationStatus load(AccountDataReader dr) {
            int id = dr.getDataInt(KEY, NEVER.id);
            return fromId(id);
        }
        
        public static CredentialsVerificationStatus load(JSONObject jso) {
            int id = jso.optInt(KEY, NEVER.id);
            return fromId(id);
        }
    }

    public static MyAccount getEmpty() {
        return MyAccount.getEmpty(MyContextHolder.get());
    }

    public static MyAccount getEmpty(MyContext myContext) {
        return Builder.getEmptyAccount(myContext, "(empty)");
    }

    public static MyAccount getEmpty(MyContext myContext, String accountName) {
        return Builder.getEmptyAccount(myContext, accountName); 
    }

    public static MyAccount fromBundle(Bundle bundle) {
        return bundle == null ? getEmpty() :
                MyContextHolder.get().persistentAccounts().fromAccountName(
                        bundle.getString(IntentExtra.ACCOUNT_NAME.key));
    }

    public boolean getCredentialsPresent() {
        return getConnection().getCredentialsPresent();
    }    
    
    public CredentialsVerificationStatus getCredentialsVerified() {
        return credentialsVerified;
    }
 
    public boolean isValidAndSucceeded() {
        return isValid() && getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED;        
    }
    
    private boolean isPersistent() {
        return accountData.isPersistent();
    }
    
    /**
     * Are authenticated users from more than one different Originating system?
     * @return count
     */
    public String shortestUniqueAccountName() {
        String uniqueName = getAccountName();

        boolean found = false;
        String possiblyUnique = getUsername();
        for (MyAccount persistentAccount : MyContextHolder.get().persistentAccounts().collection()) {
            if (!persistentAccount.toString().equalsIgnoreCase(toString()) 
                    && persistentAccount.getUsername().equalsIgnoreCase(possiblyUnique) ) {
                found = true;
                break;
            }
        }
        if (!found) {
            uniqueName = possiblyUnique;
            int indAt = uniqueName.indexOf('@');
            if (indAt > 0) {
                possiblyUnique = uniqueName.substring(0, indAt);
                for (MyAccount persistentAccount : MyContextHolder.get().persistentAccounts().collection()) {
                    if (!persistentAccount.toString().equalsIgnoreCase(toString()) ) {
                        String toCompareWith = persistentAccount.getUsername();
                        indAt = toCompareWith.indexOf('@');
                        if (indAt > 0) {
                            toCompareWith = toCompareWith.substring(0, indAt);
                        }
                        if (toCompareWith.equalsIgnoreCase(possiblyUnique) ) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    uniqueName = possiblyUnique;
                }
            }
        }
        return uniqueName;
    }

    public boolean isValid() {
        return (!deleted && version == MyAccount.ACCOUNT_VERSION) 
                && userId != 0
                && connection != null
                && oAccountName.isValid()
                && !TextUtils.isEmpty(userOid);
    }
    
    private MyAccount(MyContext myContext, AccountData accountDataIn) {
        if (accountDataIn == null) {
            accountData = AccountData.fromJson(null, false);
        } else {
            accountData = accountDataIn;
        }
        oAccountName = AccountName.fromOriginAndUserNames(myContext,
                accountData.getDataString(Origin.KEY_ORIGIN_NAME, ""), 
                accountData.getDataString(KEY_USERNAME, ""));
        version = accountData.getDataInt(KEY_VERSION, ACCOUNT_VERSION);
        deleted = accountData.getDataBoolean(KEY_DELETED, false);
        syncFrequencySeconds = accountData.getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0L);
        mIsSyncable = accountData.getDataBoolean(KEY_IS_SYNCABLE, true);
        mSyncAutomatically = accountData.getDataBoolean(KEY_SYNC_AUTOMATICALLY, true);
        userId = accountData.getDataLong(KEY_USER_ID, 0L);
        userOid = accountData.getDataString(KEY_USER_OID, "");
        setOAuth(TriState.UNKNOWN);
        credentialsVerified = CredentialsVerificationStatus.load(accountData);
    }

    private void setOAuth(TriState isOAuthTriState) {
        Origin origin = oAccountName.getOrigin();
        boolean isOAuthBoolean = true;
        if (isOAuthTriState == TriState.UNKNOWN) {
            isOAuthBoolean = accountData.getDataBoolean(KEY_OAUTH, origin.isOAuthDefault());
        } else {
            isOAuthBoolean = isOAuthTriState.toBoolean(origin.isOAuthDefault());
        }
        isOAuth = origin.getOriginType().fixIsOAuth(isOAuthBoolean);
    }

    public String getUsername() {
        return oAccountName.getUsername();
    }
    
    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id 
     */
    public String getAccountName() {
        return oAccountName.toString();
    }
    
    /**
     * @return the {@link #userId}
     */
    public long getUserId() {
        return userId;
    }

    public String getUserOid() {
        return userOid;
    }
    
    /**
     * @return The system in which the User is defined, see {@link OriginTable}
     */
    public Origin getOrigin() {
        return oAccountName.getOrigin();
    }

    public long getOriginId() {
        return oAccountName.getOrigin().getId();
    }
    
    /**
     * @return SharedPreferences of this MyAccount. Used to store preferences which are application specific
     *   i.e. excluding data specific to Account. 
     */
    public SharedPreferences getAccountPreferences() {
        SharedPreferences sp = null;
        String prefsFileName = oAccountName.prefsFilename();
        
        if (prefsFileName.length() > 0) {
            try {
                sp = SharedPreferencesUtil.getSharedPreferences(prefsFileName);
            } catch (Exception e) {
                MyLog.e(this, "Cound't get preferences '" + prefsFileName + "'", e);
                sp = null;
            }
        }
        return sp;
    }

    public Connection getConnection() {
        return connection;
    }
    
    public boolean areClientKeysPresent() {
        return connection.areOAuthClientKeysPresent();
    }
    
    public OAuthConsumerAndProvider getOAuthConsumerAndProvider() {
        return connection.getOAuthConsumerAndProvider();
    }

    public int charactersLeftForMessage(String message) {
        return oAccountName.getOrigin().charactersLeftForMessage(message);
    }

    /**
     * {@link Origin#alternativeTermForResourceId(int)}
     */
    public int alternativeTermForResourceId(int resId) {
        return oAccountName.getOrigin().alternativeTermForResourceId(resId);
    }

    /**
     * @return the oAuth
     */
    public boolean isOAuth() {
        return isOAuth;
    }

    public String getPassword() {
        return getConnection().getPassword();
    }
    
    /**
     * This is defined by Microblogging system
     * Starting from 2010-09 twitter.com allows OAuth only
     */
    public boolean canChangeOAuth() {
        return oAccountName.getOrigin().canChangeOAuth();
    }
    
    public boolean isUsernameNeededToStartAddingNewAccount() {
        return oAccountName.getOrigin().isUsernameNeededToStartAddingNewAccount(isOAuth());
    }

    public boolean isUsernameValid() {
        return oAccountName.getOrigin().isUsernameValid(getUsername());
    }
    
    public boolean isGlobalSearchSupported() {
        return getConnection().isApiSupported(ApiRoutineEnum.SEARCH_MESSAGES);
    }
    
    public void requestSync() {
        if (isPersistent()) {
           ContentResolver.requestSync(getExisingAndroidAccount(), MatchedUri.AUTHORITY, new Bundle()); 
        }
    }

    private Account getExisingAndroidAccount() {
        Account androidAccount = null;
        // Let's check if there is such an Android Account already
        android.accounts.AccountManager am = AccountManager.get(MyContextHolder.get().context());
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account account : aa) {
            if (getAccountName().equals(account.name)) {
                androidAccount = account;
                break;
            }
        }
        return androidAccount;
    }
    
    @Override
    public String toString() {
        String members = (isValid() ? "" : "(invalid) ") + "accountName:" + getAccountName() + ",";
        try {
            if (userId != 0) {
                members += "id:" + userId + ",";
            }
            if (!TextUtils.isEmpty(userOid)) {
                members += "oid:" + userOid + ",";
            }
            if (!isPersistent()) {
                members += "not persistent,";
            }
            if (isOAuth()) {
                members += "OAuth,";
            }
            if (getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                members += "verified:" + getCredentialsVerified().name() + ",";
            }
            if (getCredentialsPresent()) {
                members += "credentialsPresent,";
            }
            if (connection == null) {
                members += "connection:null,";
            }
            members += "syncFrequency:" + syncFrequencySeconds + ",";
            if (mIsSyncable) {
                members += "syncable,";
            }
            if (mSyncAutomatically) {
                members += "syncauto,";
            }
            if (deleted) {
                members += "deleted,";
            }
            if (version != ACCOUNT_VERSION) {
                members += "version:" + version + ",";
            }
        } catch (Exception e) {
            MyLog.v(this, members, e);
        }
        return MyLog.formatKeyValue(TAG, members);
    }
    
    public JSONObject toJson() throws JSONException {
        JSONObject jso = new JSONObject();
        jso.put(KEY_ACCOUNT, getAccountName());  
        jso.put(KEY_USERNAME, getUsername());  
        jso.put(KEY_USER_OID, userOid);
        jso.put(Origin.KEY_ORIGIN_NAME, oAccountName.getOriginName());
        credentialsVerified.put(jso);
        jso.put(KEY_OAUTH, isOAuth);
        jso.put(KEY_USER_ID, userId);
        if (connection != null) {
            connection.save(jso);
        }
        jso.put(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, syncFrequencySeconds);
        jso.put(KEY_IS_SYNCABLE, mIsSyncable);
        jso.put(KEY_SYNC_AUTOMATICALLY, mSyncAutomatically);
        jso.put(KEY_VERSION, version);
        return jso;
    }

    public String toAccountButtonText() {
        String accountButtonText = shortestUniqueAccountName();
        if (!isValidAndSucceeded()) {
            accountButtonText = "(" + accountButtonText + ")";
        }
        return accountButtonText;
    }

    public int numberOfAccountsOfThisOrigin() {
        int count = 0;
        for (MyAccount persistentAccount : MyContextHolder.get().persistentAccounts().collection()) {
            if (persistentAccount.getOriginId() == this.getOriginId()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * @return this account if there are no others
     */
    public MyAccount firstOtherAccountOfThisOrigin() {
        for (MyAccount persistentAccount : MyContextHolder.get().persistentAccounts().collection()) {
            if (persistentAccount.getOriginId() == this.getOriginId() 
                    && persistentAccount != this) {
                return persistentAccount;
            }
        }
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        try {
            result = prime * result + toJson().toString(2).hashCode();
        } catch (JSONException e) {
            MyLog.v(this, e);
        }
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
        MyAccount other = (MyAccount) obj;
        try {
            return toJson().toString(2).equals(other.toJson().toString(2));
        } catch (JSONException e) {
            MyLog.v(this, e);
        }
        return hashCode() == other.hashCode();
    }

    public boolean isSyncedAutomatically() {
        return mSyncAutomatically;
    }
}
