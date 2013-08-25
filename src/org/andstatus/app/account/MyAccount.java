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

package org.andstatus.app.account;

import static android.content.Context.MODE_PRIVATE;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.PeriodicSync;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Vector;

import org.andstatus.app.R;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.LatestUserMessages;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionAuthenticationException;
import org.andstatus.app.net.ConnectionCredentialsOfOtherUserException;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionUnavailableException;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.OAuthConsumerAndProvider;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Immutable class that holds MyAccount-specific information including: 
 * a Microblogging System (twitter.com, identi.ca etc.), 
 * Username in that system and {@link Connection} to it.
 * 
 * @author Yuri Volkov
 */
public class MyAccount implements AccountDataReader {
    private static final String TAG = MyAccount.class.getSimpleName();

    /** Companion class used to load/create/change/delete {@link MyAccount}'s data */
    public static class Builder implements Parcelable, AccountDataWriter {
        private static final String TAG = MyAccount.TAG + "." + Builder.class.getSimpleName();


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
         * This MyAccount: Username of the {@link MyDatabase.User} corresponding to this {@link MyAccount}
         */
        public static final String KEY_USERNAME = "username";
        /**
         * New Username typed / selected in UI
         * It doesn't immediately change "Current MyAccount"
         */
        public static final String KEY_USERNAME_NEW = "username_new";
        /**
         *  Unique originating (source) system (twitter.com, identi.ca, ... )
         */
        public static final String KEY_ORIGIN_NAME = "origin_name";
        /**
         * {@link MyDatabase.User#_ID} in our System.
         */
        public static final String KEY_USER_ID = "user_id";

        /**
         * Is OAuth on for this MyAccount?
         */
        public static final String KEY_OAUTH = "oauth";
        
        /**
         * Factory of Builder-s
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         * 
         * @param accountName - Account Guid (in the form of systemname/username - with slash "/" between them)
         *  if accountName doesn't have systemname (before slash), default system name is assumed
         * @return Builder - existed or newly created. For new Builder we assume that it is not persistent.
         */
        public static Builder newOrExistingFromAccountName(String accountName) {
            MyAccount myAccount = MyAccount.fromAccountName(accountName);
            Builder mab;
            if (myAccount == null) {
                // Create temporary MyAccount.Builder
                mab = new Builder(accountName);
            } else {
                mab = new Builder(myAccount);
            }
            return mab;
        }
        
        private MyAccount myAccount;
        
        private Builder(Parcel source) {
            myAccount = new MyAccount();
            boolean isPersistent = myAccount.getDataBoolean(KEY_PERSISTENT, false);
            myAccount.userData = source.readBundle();
            
            // Load as if the account is not persisted to force loading everything from userData
            loadFromUserData();

            // Do this as a last step
            if (isPersistent) {
                myAccount.androidAccount = myAccount.userData.getParcelable(KEY_ACCOUNT);
                if (myAccount.androidAccount == null) {
                    Log.e(TAG, "The account was marked as persistent:" + this);
                }
            }
        }
        
        /**
         * Creates new account, which is not Persistent yet
         * @param accountName
         */
        private Builder (String accountName) {
            myAccount = new MyAccount();
            myAccount.oAccountName = AccountName.fromAccountName(accountName);
            myAccount.isOAuth = myAccount.oAccountName.getOrigin().isOAuthDefault();
            myAccount.syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
            setConnection();
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "New temporary account created: " + this.toString());
            }
        }

        private Builder (MyAccount ma) {
            this.myAccount = ma;
        }
        
        /**
         * Loads existing account from Persistence 
         * @param account should not be null
         */
        Builder(android.accounts.Account account) {
            myAccount = new MyAccount();
            if (account == null) {
                throw new IllegalArgumentException(TAG + " null account is not allowed in the constructor");
            }
            myAccount.androidAccount = account;

            myAccount.oAccountName = AccountName.fromAccountName(myAccount.androidAccount.name);
            
            // Load stored data for the User
            myAccount.credentialsVerified = CredentialsVerificationStatus.load(myAccount);
            myAccount.isOAuth = myAccount.getDataBoolean(KEY_OAUTH, myAccount.oAccountName.getOrigin().isOAuthDefault());
            myAccount.userId = myAccount.getDataLong(KEY_USER_ID, 0L);
            myAccount.syncFrequencySeconds = myAccount.getDataLong(MyPreferences.KEY_FETCH_FREQUENCY, 0);
            
            fixMyAccount();

            setConnection();
            
            if (!myAccount.getCredentialsPresent()) {
                if (myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    Log.e(TAG, "User's credentials were lost?! Fixing...");
                    setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                    save();
                }
            }
            
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Loaded " + this.toString());
            }
        }

        /** Fix inconsistencies with changed environment... */
        private void fixMyAccount() {
            boolean changed = false;
            if (myAccount.userId==0) {
                changed = true;
                assignUserId();
                Log.e(TAG, "MyAccount '" + myAccount.getAccountName() + "' was not connected to the User table. UserId=" + myAccount.userId);
            }
            if (myAccount.syncFrequencySeconds==0) {
                myAccount.syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
                ContentResolver.setIsSyncable(myAccount.androidAccount, MyProvider.AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(myAccount.androidAccount, MyProvider.AUTHORITY, true);
                changed = true;
            }
            if (myAccount.oAccountName.getOrigin().isOAuthDefault() != myAccount.isOAuth()) {
                if (!myAccount.oAccountName.getOrigin().canChangeOAuth()) {
                    setOAuth(myAccount.oAccountName.getOrigin().isOAuthDefault());
                    changed = true;
                }
            }
            if (changed) {
                save(true);
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
        private boolean deleteData() {
            boolean ok = true;

            // Old preferences file may be deleted, if it exists...
            ok = SharedPreferencesUtil.delete(MyPreferences.getContext(), myAccount.oAccountName.prefsFileName());

            if (isPersistent()) {
                if (myAccount.userId != 0) {
                    // TODO: Delete databases for this User
                    
                    myAccount.userId = 0;
                }

                // We don't delete Account from Account Manager here
                myAccount.androidAccount = null;
            }
            return ok;
        }

        /**
         * Loads account from userData 
         */
        private void loadFromUserData() {
            String originName = Origin.ORIGIN_NAME_TWITTER;
            String username = "";
            if (isPersistent()) {
                originName = AccountName.accountNameToOriginName(myAccount.androidAccount.name);
                username = AccountName.accountNameToUsername(myAccount.androidAccount.name);
            } else {
                originName = myAccount.getDataString(KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER);
                username = myAccount.getDataString(KEY_USERNAME, "");
            }
            myAccount.oAccountName = AccountName.fromOriginAndUserNames(originName, username);
            
            // Load stored data for the MyAccount
            myAccount.credentialsVerified = CredentialsVerificationStatus.load(myAccount);
            myAccount.isOAuth = myAccount.getDataBoolean(KEY_OAUTH, myAccount.oAccountName.getOrigin().isOAuthDefault());
            myAccount.userId = myAccount.getDataLong(KEY_USER_ID, 0L);
            myAccount.syncFrequencySeconds = myAccount.getDataLong(MyPreferences.KEY_FETCH_FREQUENCY, 0L);
            setConnection();
            
            if (isPersistent() && myAccount.userId==0) {
                assignUserId();
                Log.e(TAG, "MyAccount '" + myAccount.getAccountName() + "' was not connected to the User table. UserId=" + myAccount.userId);
            }
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Loaded " + this.toString());
            }
        }
        
        @Override
        public void setDataString(String key, String value) {
            try {
                if (isPersistent()) {
                    android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
                    am.setUserData(myAccount.androidAccount, key, value);
                } else {
                    if (TextUtils.isEmpty(value)) {
                        myAccount.userData.remove(key);
                    } else {
                        myAccount.userData.putString(key, value);
                    }
                }
            } catch (Exception e) {}
        }

        @Override
        public void setDataInt(String key, int value) {
            try {
                setDataString(key, Integer.toString(value));
            } catch (Exception e) {}
        }

        public void setDataLong(String key, long value) {
            try {
                if (key.equals(MyPreferences.KEY_FETCH_FREQUENCY) && isPersistent()) {
                    // See
                    // http://developer.android.com/reference/android/content/ContentResolver.html#addPeriodicSync(android.accounts.Account, java.lang.String, android.os.Bundle, long)
                    // and
                    // http://stackoverflow.com/questions/11090604/android-syncadapter-automatically-initialize-syncing
                    ContentResolver.removePeriodicSync(myAccount.androidAccount, MyProvider.AUTHORITY, new Bundle());
                    if (value > 0) {
                        ContentResolver.addPeriodicSync(myAccount.androidAccount, MyProvider.AUTHORITY, new Bundle(), value);
                    }
                } else {
                    setDataString(key, Long.toString(value));
                }
            } catch (Exception e) {}
        }
        
        public void setDataBoolean(String key, boolean value) {
            try {
                setDataString(key, Boolean.toString(value));
            } catch (Exception e) {}
        }

        public void save() {
            save(false);
        }
        

        /**
         * Save this MyAccount:
         * 1) to internal Bundle (userData). 
         * 2) If it is not Persistent yet and may be added to AccountManager, do it (i.e. Persist it). 
         * 3) If it isPersitent, save everything to AccountManager also. 
         */
        public void save(boolean saveChangesSilently) {
            boolean changed = false;
            
            try {
                if (!isPersistent() && (myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED)) {
                    try {
                        changed = true;
                        // Now add this account to the Account Manager
                        // See {@link com.android.email.provider.EmailProvider.createAccountManagerAccount(Context, String, String)}
                        AccountManager accountManager = AccountManager.get(MyPreferences.getContext());

                        /* Note: We could add userdata from {@link userData} Bundle, 
                         * but we decided to add it below one by one item
                         */

                        // Create account to be persisted
                        myAccount.androidAccount = new android.accounts.Account(myAccount.getAccountName(), AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                        accountManager.addAccountExplicitly(myAccount.androidAccount, myAccount.getPassword(), null);
                        
                        ContentResolver.setIsSyncable(myAccount.androidAccount, MyProvider.AUTHORITY, 1);
                        
                        // This is not needed because we don't use the "network tickles"... yet?!
                        // See http://stackoverflow.com/questions/5013254/what-is-a-network-tickle-and-how-to-i-go-about-sending-one
                        ContentResolver.setSyncAutomatically(myAccount.androidAccount, MyProvider.AUTHORITY, true);

                        // Without SyncAdapter we got the error:
                        // SyncManager(865): can't find a sync adapter for SyncAdapterType Key 
                        // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app}, removing settings for it
                        
                        MyLog.v(TAG, "Persisted " + myAccount.getAccountName());
                    } catch (Exception e) {
                        Log.e(TAG, "Adding Account to AccountManager: " + e.getMessage());
                        myAccount.androidAccount = null;
                    }
                }
                
                if (myAccount.getDataString(KEY_USERNAME, "").compareTo(myAccount.oAccountName.getUsername()) !=0 ) {
                    setDataString(KEY_USERNAME, myAccount.oAccountName.getUsername());
                    changed = true;
                }
                if (myAccount.oAccountName.getOriginName().compareTo(myAccount.getDataString(KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER)) != 0) {
                    setDataString(KEY_ORIGIN_NAME, myAccount.oAccountName.getOriginName());
                    changed = true;
                }
                if (myAccount.credentialsVerified != CredentialsVerificationStatus.load(myAccount)) {
                    myAccount.credentialsVerified.put(this);
                    changed = true;
                }
                if (myAccount.isOAuth != myAccount.getDataBoolean(KEY_OAUTH, myAccount.oAccountName.getOrigin().isOAuthDefault())) {
                    setDataBoolean(KEY_OAUTH, myAccount.isOAuth);
                    changed = true;
                }
                if (myAccount.userId != myAccount.getDataLong(KEY_USER_ID, 0L)) {
                    setDataLong(KEY_USER_ID, myAccount.userId);
                    changed = true;
                }
                if (myAccount.connection.save(this)) {
                    changed = true;
                }
                if (isPersistent() != myAccount.getDataBoolean(KEY_PERSISTENT, false)) {
                    setDataBoolean(KEY_PERSISTENT, isPersistent());
                    changed = true;
                }
                if (myAccount.syncFrequencySeconds != myAccount.getDataInt(MyPreferences.KEY_FETCH_FREQUENCY, 0)) {
                    setDataLong(MyPreferences.KEY_FETCH_FREQUENCY, myAccount.syncFrequencySeconds); 
                    changed = true;
                }

                if (!saveChangesSilently && changed && isPersistent()) {
                    MyPreferences.onPreferencesChanged();
                }

                MyLog.v(TAG, "Saved " + (changed ? " changed " : " no changes " ) + this);
            } catch (Exception e) {
                Log.e(TAG, "saving " + myAccount.getAccountName() + ": " + e.toString());
                e.printStackTrace();
            }
        }
        
        

        /**
         * TODO: Make compatible with Pump.io 
         * Verify the user's credentials. Returns true if authentication was
         * successful
         * 
         * @see CredentialsVerificationStatus
         * @param reVerify Verify even if it was verified already
         * @return boolean
         * @throws ConnectionException
         * @throws ConnectionUnavailableException
         * @throws ConnectionAuthenticationException
         * @throws SocketTimeoutException
         * @throws ConnectionCredentialsOfOtherUserException
         */
        public boolean verifyCredentials(boolean reVerify) throws ConnectionException,
                ConnectionUnavailableException, ConnectionAuthenticationException,
                SocketTimeoutException, ConnectionCredentialsOfOtherUserException {
            boolean ok = false;
            if (!reVerify) {
                if (myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    ok = true;
                }
            }
            if (!ok) {
                MbUser user = null;
                try {
                    user = myAccount.getConnection().verifyCredentials();
                    ok = (!user.isEmpty());
                } finally {
                    String newName = "";
                    boolean credentialsOfOtherUser = false;
                    boolean errorSettingUsername = false;
                    if (ok) {
                        if (TextUtils.isEmpty(user.oid)) {
                            ok = false;
                        }
                    }
                    if (ok) {
                        newName = user.userName;
                        ok = UserNameUtil.isUsernameValid(newName);
                        errorSettingUsername = !ok;
                    }

                    if (ok) {
                        // We are comparing user names ignoring case, but we fix correct case
                        // as the Originating system tells us. 
                        if (!TextUtils.isEmpty(myAccount.getUsername()) && myAccount.getUsername().compareToIgnoreCase(newName) != 0) {
                            // Credentials belong to other User ??
                            ok = false;
                            credentialsOfOtherUser = true;
                        }
                    }
                    if (ok) {
                        setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
                    }
                    if (ok && !isPersistent()) {
                        // Now we know the name (or proper case of the name) of this User!
                        // We don't recreate MyAccount object for the new name
                        //   in order to preserve credentials.
                        myAccount.oAccountName = AccountName.fromOriginAndUserNames(myAccount.oAccountName.getOriginName(), newName);
                        if (myAccount.userId == 0) {
                            assignUserId();
                        }
                        save();
                        setConnection();
                    }
                    if (!ok) {
                        setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
                    }
                    save();

                    if (credentialsOfOtherUser) {
                        Log.e(TAG, MyPreferences.getContext().getText(R.string.error_credentials_of_other_user) + ": "
                                + newName);
                        throw (new ConnectionCredentialsOfOtherUserException(newName));
                    }
                    if (errorSettingUsername) {
                        String msg = MyPreferences.getContext().getText(R.string.error_set_username) + newName;
                        Log.e(TAG, msg);
                        throw (new ConnectionAuthenticationException(msg));
                    }
                }
            }
            return ok;
        }
        
        public void setUserTokenWithSecret(String token, String secret) {
            myAccount.getConnection().setUserTokenWithSecret(token, secret);
        }

        public void setCredentialsVerificationStatus(CredentialsVerificationStatus cv) {
            myAccount.credentialsVerified = cv;
            if (cv != CredentialsVerificationStatus.SUCCEEDED) {
                myAccount.connection.clearAuthInformation();
            }
        }

        public void setOAuth(boolean isOAuth) {
            if (!myAccount.oAccountName.getOrigin().canChangeOAuth()) {
                isOAuth = myAccount.oAccountName.getOrigin().isOAuthDefault();
            }
            if (myAccount.isOAuth != isOAuth) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                myAccount.isOAuth = isOAuth;
                setConnection();
            }
        }
        
        private void setConnection() {
            myAccount.oAccountName.getOrigin().setOAuth(myAccount.isOAuth);
            myAccount.connection = myAccount.oAccountName.getOrigin().getConnection();
            myAccount.connection.setAccountData(myAccount);
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
            myAccount.userId = MyProvider.userNameToId(myAccount.getOriginId(), myAccount.getUsername());
            if (myAccount.userId == 0) {
                DataInserter di = new DataInserter(myAccount, MyPreferences.getContext(), TimelineTypeEnum.ALL);
                try {
                    // Construct "User" from available account info
                    // We need this User in order to be able to link Messages to him
                    MbUser mbUser = MbUser.fromOriginAndUserName(myAccount.getOriginId(), myAccount.getUsername());
                    LatestUserMessages lum = new LatestUserMessages();
                    myAccount.userId = di.insertOrUpdateUser(mbUser, lum);
                    lum.save();
                } catch (Exception e) {
                    Log.e(TAG, "Construct user: " + e.toString());
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
            // We don't need this until it is persisted
            if (isPersistent()) {
                myAccount.userData.putParcelable(KEY_ACCOUNT, myAccount.androidAccount);
            }
            dest.writeParcelable(myAccount.userData, flags);
        }
        
        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @Override
            public Builder createFromParcel(Parcel source) {
                return new Builder(source);
            }

            @Override
            public Builder[] newArray(int size) {
                return new Builder[size];
            }
        };

        @Override
        public boolean dataContains(String key) {
            return myAccount.dataContains(key);
        }

        @Override
        public int getDataInt(String key, int defValue) {
            return myAccount.getDataInt(key, defValue);
        }

        @Override
        public String getDataString(String key, String defValue) {
            return myAccount.getDataString(key, defValue);
        }
        
        @Override
        public long getOriginId() {
            return myAccount.getOriginId();
        }

        @Override
        public String getUsername() {
            return myAccount.getUsername();
        }
        
        @Override
        public String toString() {
            return myAccount.toString();
        }
    }
    
    /**
     * Persistence key for the Name of the default account
     */
    public static final String KEY_DEFAULT_ACCOUNT_NAME = "default_account_name";
    /**
     * Name of the default account. The name is the same for this class and for {@link android.accounts.Account}
     */
    private static String defaultAccountName = "";
    /**
     * Name of "current" account: it is not stored when application is killed
     */
    private static String currentAccountName = "";
    
    private AccountName oAccountName = AccountName.fromAccountName("");
    private Connection connection = null;
    
    /**
     * Android Account associated with this MyAccount
     */
    private android.accounts.Account androidAccount;
    
    /**
     * User Data associated with this Account
     * It's mainly used when the MyAccount is not Persisted yet.
     */
    private Bundle userData = new Bundle();
    
    /**
     * Id in the database, see {@link MyDatabase.User#_ID}
     */
    private long userId = 0;
    

    /**
     * Was this user authenticated last time _current_ credentials were verified?
     * CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private CredentialsVerificationStatus credentialsVerified = CredentialsVerificationStatus.NEVER;

    /**
     * Is this user authenticated with OAuth?
     */
    private boolean isOAuth = true;

    private long syncFrequencySeconds = 0;
    
    /**
     *  TODO: Use instance fields instead of ordinals (see [EffectiveJava] Item 31)
     */
    public enum CredentialsVerificationStatus {
        /** 
         * NEVER - means that User was never successfully authenticated with current credentials.
         *  This is why we reset the state to NEVER every time credentials have been changed.
         */
        NEVER, 
        FAILED,
        /** The User was successfully authenticated */
        SUCCEEDED;

        /*
         * Methods to persist in SharedPreferences
         */
        private static final String KEY = "credentials_verified";

        public static CredentialsVerificationStatus load(SharedPreferences sp) {
            int ind = sp.getInt(KEY, NEVER.ordinal());
            CredentialsVerificationStatus cv = CredentialsVerificationStatus.values()[ind];
            return cv;
        }
        
        public static CredentialsVerificationStatus load(AccountDataReader dr) {
            int ind = dr.getDataInt(KEY, NEVER.ordinal());
            CredentialsVerificationStatus cv = CredentialsVerificationStatus.values()[ind];
            return cv;
        }
        
        public void put(AccountDataWriter dw) {
            dw.setDataInt(KEY, ordinal());
        }
    }

    @Override
    public int getDataInt(String key, int defValue) {
        int value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = Integer.parseInt(str);
            }
        } catch (Exception e) {}
        return value;
    }

    private long getDataLong(String key, long defValue) {
        long value = defValue;
        try {
            if (key.equals(MyPreferences.KEY_FETCH_FREQUENCY) && isPersistent()) {
                List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(androidAccount, MyProvider.AUTHORITY);
                // Take care of the first in the list
                if (syncs.size() > 0) {
                    value = syncs.get(0).period;
                }
            } else {
                String str = getDataString(key, "null");
                if (str.compareTo("null") != 0) {
                    value = Long.parseLong(str);
                }
            }
        } catch (Exception e) {}
        return value;
    }

    private boolean getDataBoolean(String key, boolean defValue) {
        boolean value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = SharedPreferencesUtil.isTrue(str);
            }
        } catch (Exception e) {}
        return value;
    }

    /**
     * User Data associated with the account
     */
    @Override
    public String getDataString(String key, String defValue) {
        String value = defValue;
        if (isPersistent()) {
            android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
            String str = am.getUserData(androidAccount, key);
            if (!TextUtils.isEmpty(str)) {
                value = str;
            }
            // And cache retrieved value (Do we really need this?)
            if (TextUtils.isEmpty(value)) {
                userData.remove(key);
            } else {
                userData.putString(key, value);
            }
        } else {
            String str = userData.getString(key);
            if (!TextUtils.isEmpty(str)) {
                value = str;
            }
        }
        
        return value;
    }
    
    @Override
    public boolean dataContains(String key) {
        boolean contains = false;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                contains = true;
            }
        } catch (Exception e) {}
        return contains;
    }
    
    public boolean getCredentialsPresent() {
        return getConnection().getCredentialsPresent();
    }    
    
    public CredentialsVerificationStatus getCredentialsVerified() {
        return credentialsVerified;
    }
    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     */
    public static void forget() {
        persistentAccounts.clear();
    }
    
    /**
     * Get instance of current MyAccount (MyAccount selected by the user). The account isPersistent.
     * As a side effect the function changes current account if old value is not valid.
     * @return MyAccount or null if no persistent accounts exist
     */
    public static MyAccount getCurrentAccount() {
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
            if (persistentAccounts.size() > 0) {
                ma = persistentAccounts.iterator().next();
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
    public static String getCurrentAccountName() {
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
    public static long getCurrentAccountUserId() {
        MyAccount ma = getCurrentAccount();
        if (ma != null) {
            return ma.getUserId();
        }
        return 0;
    }
    
    private static List<MyAccount> persistentAccounts = new Vector<MyAccount>();
    
    /**
     * Get list of all persistent accounts
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link #getCredentialsVerified()} is the main differentiator.
     * 
     * @param context
     * @return Array of users, not null 
     */
    public static MyAccount[] list() {
        return persistentAccounts.toArray(new MyAccount[persistentAccounts.size()]);
    }
    
    public static int numberOfPersistentAccounts() {
        return persistentAccounts.size();
    }

    /**
     * @return Is this object persistent 
     */
    private boolean isPersistent() {
        return (androidAccount != null);
    }
    
    /**
     * Are authenticated users from more than one different Originating system?
     * @return count
     */
    public static boolean moreThanOneOriginatingSystem() {
        int count = 0;
        long originId = 0;

        for (MyAccount persistentAccount : persistentAccounts) {
            if (originId != persistentAccount.getOriginId() ) {
                count += 1;
                originId = persistentAccount.getOriginId();
                if (count >1) {
                    break;
                }
            }
        }
        return (count>1);
    }
    
    /**
     * Initialize the class. Required before first call to other methods.
     */
    public static void initialize(Context context) {
        persistentAccounts.clear();
        defaultAccountName = MyPreferences.getDefaultSharedPreferences().getString(KEY_DEFAULT_ACCOUNT_NAME, "");

        android.accounts.AccountManager am = AccountManager.get(context);
        android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
        for (android.accounts.Account account : aa) {
            persistentAccounts.add(new Builder(account).getAccount());
        }
        MyLog.v(TAG, "Account list initialized, " + persistentAccounts.size() + " accounts");
    }

    /**
     * Get MyAccount by the UserId. Valid User may not have an Account (in AndStatus)
     * @param userId
     * @return null if not found
     */
    public static MyAccount fromUserId(long userId) {
        MyAccount ma = null;
        for (MyAccount persistentAccount : persistentAccounts) {
            if (persistentAccount.getUserId() == userId) {
                ma = persistentAccount;
                break;
            }
        }
        return ma;
    }

    /**
     * Return first found MyAccount with provided originId
     * @param originId
     * @return null if not found
     */
    private static MyAccount findFirstMyAccountByOriginId(long originId) {
        MyAccount ma = null;
        for (MyAccount persistentAccount : persistentAccounts) {
            if (persistentAccount.getOriginId() == originId) {
                ma = persistentAccount;
                break;
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
     * @param timelineIsOfThisUserId The message is in his timeline. 0 if the message doesn't belong to any timeline
     * @param preferredAccountUserId Preferred account (or 0), used in a case userId is not an Account 
     *          or is not linked to the message 
     * @return null if nothing suitable found
     */
    public static MyAccount getAccountLinkedToThisMessage(long messageId, long timelineIsOfThisUserId, long preferredAccountUserId)
    {
        MyAccount ma = fromUserId(timelineIsOfThisUserId);
        if (messageId == 0 || ma == null) {
            ma = fromUserId(preferredAccountUserId);
        }
        long originId = MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.ORIGIN_ID, messageId);
        if (ma == null || originId != ma.getOriginId()) {
           ma = findFirstMyAccountByOriginId(originId); 
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "getMyAccountLinkedToThisMessage msgId=" + messageId +"; userId=" + timelineIsOfThisUserId 
                    + " -> account=" + (ma==null ? "null" : ma.getAccountName()));
        }
        return ma;
    }
    
    /**
     * Find persistent MyAccount by accountName in local cache AND in Android
     * AccountManager
     * 
     * @return null if was not found
     */
    public static MyAccount fromAccountName(String accountName_in) {
        MyAccount myAccount = null;
        AccountName accountName = AccountName.fromAccountName(accountName_in);

        for (MyAccount persistentAccount : persistentAccounts) {
            if (persistentAccount.getAccountName().compareTo(accountName.toString()) == 0) {
                myAccount = persistentAccount;
                break;
            }
        }
        if (myAccount == null) {
            // Try to find persisted Account which was not loaded yet
            if (!TextUtils.isEmpty(accountName.toString())) {
                android.accounts.Account[] androidAccounts = AccountManager.get(
                        MyPreferences.getContext()).getAccountsByType(
                        AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                for (android.accounts.Account androidAccount : androidAccounts) {
                    if (accountName.compareTo(androidAccount.name) == 0) {
                        myAccount = new Builder(androidAccount).getAccount();
                        persistentAccounts.add(myAccount);
                        // It looks like preferences has changed...
                        MyPreferences.onPreferencesChanged();
                        break;
                    }
                }
            }
        }
        return myAccount;
    }
    
    /**
     * Delete everything about the MyAccount
     * 
     * @return Was the MyAccount (and Account) deleted?
     */
    public static boolean delete(MyAccount ma) {
        boolean isDeleted = false;

        // Delete the User's object from the list
        boolean found = false;
        for (MyAccount persistentAccount : persistentAccounts) {
            if (persistentAccount.equals(ma)) {
                found = true;
                break;
            }
        }
        if (found) {
            new Builder(ma).deleteData();

            // And delete the object from the list
            persistentAccounts.remove(ma);

            isDeleted = true;
            MyPreferences.onPreferencesChanged();
        }
        return isDeleted;
    }

    /**
     * Set provided MyAccount as Current one.
     * Current account selection is not persistent
     */
    public static synchronized void setCurrentAccount(MyAccount ma) {
        if (ma != null) {
            currentAccountName = ma.getAccountName();
        }
    }

    /**
     * Set provided MyAccount as a default one.
     * Default account selection is persistent
     */
    private static synchronized void setDefaultAccount(MyAccount ma) {
        if (ma != null) {
            defaultAccountName = ma.getAccountName();
        }
        MyPreferences.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
    }
    
    public static void onMyPreferencesChanged() {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount persistentAccount : persistentAccounts) {
            Builder builder = new Builder(persistentAccount);
            builder.myAccount.syncFrequencySeconds = syncFrequencySeconds;
            builder.save();
        }
    }
    
    private MyAccount() {};

    /**
     * @return the username
     */
    @Override
    public String getUsername() {
        return oAccountName.getUsername();
    }

    public AccountName getName() {
        return oAccountName;
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
    
    /**
     * @return id of the system in which the User is defined, see {@link MyDatabase.User#ORIGIN_ID}
     */
    @Override
    public long getOriginId() {
        return oAccountName.getOrigin().getId();
    }
     
    /**
     * @return SharedPreferences of this MyAccount. Used to store preferences which are application specific
     *   i.e. excluding data specific to Account. 
     */
    public SharedPreferences getAccountPreferences() {
        SharedPreferences sp = null;
        String prefsFileName = oAccountName.prefsFileName();
        
        if (prefsFileName.length() > 0) {
            try {
                sp = MyPreferences.getSharedPreferences(prefsFileName, MODE_PRIVATE);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Cound't get preferences '" + prefsFileName + "'");
                sp = null;
            }
        }
        return sp;
    }

    public Connection getConnection() {
        return connection;
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
     * {@link Origin#messagePermalink(String, String)}
     */
    public String messagePermalink(String userName, String messageOid) {
        return oAccountName.getOrigin().messagePermalink(userName, messageOid);
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
     * This is defined by tweet server
     * Starting from 2010-09 twitter.com allows OAuth only
     */
    public boolean canChangeOAuth() {
        return oAccountName.getOrigin().canChangeOAuth();
    }
    
    /**
     * Can user set username for the new user manually?
     * Current implementation of twitter.com authentication doesn't use this attribute, so it's disabled
     */
    public boolean canSetUsername() {
        return oAccountName.getOrigin().canSetUsername(isOAuth());
    }
    
    public void requestSync() {
        if (isPersistent()) {
           ContentResolver.requestSync(androidAccount, MyProvider.AUTHORITY, new Bundle()); 
        }
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String str = TAG;
        String members = getAccountName();
        try {
            if (isPersistent()) {
                members += "; persistent";
            }
            if (isOAuth()) {
                members += "; OAuth";
            }
            members += "; verified=" + getCredentialsVerified().name();
            if (getCredentialsPresent()) {
                members += "; credentials present";
            }
        } catch (Exception e) {}
        return str + "{" + members + "}";
    }
}
