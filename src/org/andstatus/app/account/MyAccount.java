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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;

import java.net.SocketTimeoutException;
import java.util.regex.Pattern;
import java.util.Vector;

import org.andstatus.app.R;
import org.andstatus.app.data.DataInserter;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionAuthenticationException;
import org.andstatus.app.net.ConnectionCredentialsOfOtherUserException;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionOAuth;
import org.andstatus.app.net.ConnectionUnavailableException;
import org.andstatus.app.net.MyOAuth;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONObject;

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
         * @param accountName - AccountGuid (in the form of systemname/username - with slash "/" between them)
         *  if accountName doesn't have systemname (before slash), default system name is assumed
         * @return Builder - existed or newly created. For new Builder we assume that it is not persistent.
         */
        public static Builder valueOf(String accountName) {
            int ind = indexOfMyAccount(accountName);
            Builder mab;
            if (ind < 0) {
                // Create temporary MyAccount.Builder
                mab = new Builder(accountName);
            } else {
                mab = new Builder(mMyAccounts.elementAt(ind));
            }
            return mab;
        }
        
        
        /**
         * The whole data is here
         */
        private MyAccount ma;
        
        private Builder(Parcel source) {
            ma = new MyAccount();
            boolean isPersistent = ma.getDataBoolean(KEY_PERSISTENT, false);
            ma.mUserData = source.readBundle();
            
            // Load as if the account is not persisted to force loading everything from mUserData
            loadMyAccount(null, false);

            // Do this as a last step
            if (isPersistent) {
                ma.mAccount = ma.mUserData.getParcelable(KEY_ACCOUNT);
                if (ma.mAccount == null) {
                    isPersistent = false;
                    Log.e(TAG, "The account was marked as persistent:" + this);
                }
                ma.mIsPersistent = isPersistent;
            }
        }
        
        /**
         * Creates new account, which is not Persistent yet
         * @param accountName
         */
        private Builder (String accountName) {
            ma = new MyAccount();
            ma.mOrigin = Origin.getOrigin(accountNameToOriginName(accountName));
            ma.mUsername = accountNameToUsername(accountName);
            ma.mOAuth = ma.mOrigin.isOAuth();
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "New temporary account created: " + this.toString());
            }
        }

        private Builder (MyAccount ma) {
            this.ma = ma;
        }
        
        /**
         * Loads existing account from Persistence 
         * @param account should not be null
         */
        Builder(android.accounts.Account account) {
            ma = new MyAccount();
            if (account == null) {
                throw new IllegalArgumentException(TAG + " null account is not allowed the constructor");
            }
            ma.mAccount = account;
            ma.mIsPersistent = true;

            ma.mOrigin = Origin.getOrigin(accountNameToOriginName(ma.getAccount().name));
            ma.mUsername = accountNameToUsername(ma.getAccount().name);
            
            // Load stored data for the User
            ma.mCredentialsVerified = CredentialsVerified.load(ma);
            ma.mOAuth = ma.getDataBoolean(KEY_OAUTH, ma.mOrigin.isOAuth());
            ma.mUserId = ma.getDataLong(KEY_USER_ID, 0L);
            
            if (ma.mUserId==0) {
                setUsernameAuthenticated(ma.mUsername);
                Log.e(TAG, "MyAccount '" + ma.getUsername() + "' was not connected to the User table. UserId=" + ma.mUserId);
            }
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Loaded " + this.toString());
            }
        }
        
        public MyAccount getMyAccount() {
            return ma;
        }
        
        /**
         * Clear Authentication information
         * 
         * @param context
         */
        public void clearAuthInformation() {
            setCredentialsVerified(CredentialsVerified.NEVER);
            ma.getConnection().clearAuthInformation();
        }

        /**
         * Delete all User's data
         * @return true = success 
         */
        private boolean deleteData() {
            boolean ok = true;

            // Old preferences file may be deleted, if it exists...
            ok = SharedPreferencesUtil.delete(MyPreferences.getContext(), ma.prefsFileName());

            if (ma.isPersistent()) {
                if (ma.mUserId != 0) {
                    // TODO: Delete databases for this User
                    
                    ma.mUserId = 0;
                }

                // We don't delete Account from Account Manager here
                ma.mAccount = null;
                ma.mIsPersistent = false;
            }
            return ok;
        }


        /**
         * Loads existing account from Persistence or from mUserData in a case the account is not persistent 
         * @param account should not be null
         */
        private void loadMyAccount(android.accounts.Account account, boolean isPersistent) {
            ma.mIsPersistent = isPersistent;
            if (ma.isPersistent() && account == null) {
                throw new IllegalArgumentException(TAG + " null account for persistent MyAccount is not allowed");
            }
            ma.mAccount = account;

            String originName = Origin.ORIGIN_NAME_TWITTER;
            String userName = "";
            if (ma.isPersistent()) {
                originName = accountNameToOriginName(ma.getAccount().name);
                userName = accountNameToUsername(ma.getAccount().name);
            } else {
                originName = ma.getDataString(KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER);
                userName = ma.getDataString(KEY_USERNAME, "");
            }
            ma.mOrigin = Origin.getOrigin(originName);
            ma.mUsername = fixUsername(userName);
            
            // Load stored data for the MyAccount
            ma.mCredentialsVerified = CredentialsVerified.load(ma);
            ma.mOAuth = ma.getDataBoolean(KEY_OAUTH, ma.mOrigin.isOAuth());
            ma.mUserId = ma.getDataLong(KEY_USER_ID, 0L);
            
            if (ma.mUserId==0) {
                setUsernameAuthenticated(ma.mUsername);
                Log.e(TAG, "MyAccount '" + ma.getUsername() + "' was not connected to the User table. UserId=" + ma.mUserId);
            }
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Loaded " + this.toString());
            }
        }
        
        @Override
        public void setDataString(String key, String value) {
            try {
                if (TextUtils.isEmpty(value)) {
                    ma.mUserData.remove(key);
                } else {
                    ma.mUserData.putString(key, value);
                }
                if (ma.isPersistent()) {
                    if (ma.getAccount() == null) {
                        Log.e(TAG, "setDataString key=" + key + "; mAccount is null ");
                        return;
                    }
                    android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
                    am.setUserData(ma.getAccount(), key, value);
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
                setDataString(key, Long.toString(value));
            } catch (Exception e) {}
        }
        
        public void setDataBoolean(String key, boolean value) {
            try {
                setDataString(key, Boolean.toString(value));
            } catch (Exception e) {}
        }
        

        /**
         * Save this MyAccount:
         * 1) to internal Bundle (mUserData). 
         * 2) If it is not Persistent yet and may be added to AccountManager, do it (i.e. Persist it). 
         * 3) If it isPersitent, save everything to AccountManager also. 
         * @return true if completed successfully
         */
        public boolean save() {
            boolean ok = false;
            boolean changed = false;
            
            try {
                if (!ma.isPersistent() && (ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED)) {
                    try {
                        // Now add this account to the Account Manager
                        // See {@link com.android.email.provider.EmailProvider.createAccountManagerAccount(Context, String, String)}
                        AccountManager accountManager = AccountManager.get(MyPreferences.getContext());

                        /* Note: We could add userdata from {@link mUserData} Bundle, 
                         * but we decided to add it below one by one item
                         */
                        accountManager.addAccountExplicitly(ma.getAccount(), ma.getPassword(), null);
                        // Immediately mark as persistent
                        ma.mIsPersistent = true;
                        
                        // TODO: This is not enough, we need "sync adapter":
                        // SyncManager(865): can't find a sync adapter for SyncAdapterType Key 
                        // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app}, removing settings for it
                        ContentResolver.setIsSyncable(ma.getAccount(), MyProvider.AUTHORITY, 1);
                        ContentResolver.setSyncAutomatically(ma.getAccount(), MyProvider.AUTHORITY, true);
                        
                        MyLog.v(TAG, "Persisted " + ma.getAccountGuid());
                    } catch (Exception e) {
                        Log.e(TAG, "Adding Account to AccountManager: " + e.getMessage());
                    }
                }
                
                if (ma.getDataString(KEY_USERNAME, "").compareTo(ma.mUsername) !=0 ) {
                    setDataString(KEY_USERNAME, ma.mUsername);
                    changed = true;
                }
                if (ma.mOrigin.getName().compareTo(ma.getDataString(KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER)) != 0) {
                    setDataString(KEY_ORIGIN_NAME, ma.mOrigin.getName());
                    changed = true;
                }
                if (ma.mCredentialsVerified != CredentialsVerified.load(ma)) {
                    ma.mCredentialsVerified.put(this);
                    changed = true;
                }
                if (ma.mOAuth != ma.getDataBoolean(KEY_OAUTH, ma.mOrigin.isOAuth())) {
                    setDataBoolean(KEY_OAUTH, ma.mOAuth);
                    changed = true;
                }
                if (ma.mUserId != ma.getDataLong(KEY_USER_ID, 0L)) {
                    setDataLong(KEY_USER_ID, ma.mUserId);
                    changed = true;
                }
                if (ma.getConnection().save(this)) {
                    changed = true;
                }
                if (ma.mIsPersistent != ma.getDataBoolean(KEY_PERSISTENT, false)) {
                    setDataBoolean(KEY_PERSISTENT, ma.mIsPersistent);
                    changed = true;
                }

                if (changed && ma.isPersistent()) {
                    MyPreferences.setPreferencesChangedNow();
                }

                MyLog.v(TAG, "Saved " + (changed ? " changed " : " no changes " ) + this);
                ok = true;
            } catch (Exception e) {
                Log.e(TAG, "saving " + ma.getAccountGuid() + ": " + e.toString());
                e.printStackTrace();
                ok = false;
            }
            return ok;
        }
        
        

        /**
         * Verify the user's credentials. Returns true if authentication was
         * successful
         * 
         * @see CredentialsVerified
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
                if (ma.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                    ok = true;
                }
            }
            if (!ok) {
                JSONObject jso = null;
                try {
                    jso = ma.getConnection().verifyCredentials();
                    ok = (jso != null);
                } finally {
                    String newName = null;
                    boolean credentialsOfOtherUser = false;
                    boolean errorSettingUsername = false;
                    if (ok) {
                        if (jso.optInt("id") < 1) {
                            ok = false;
                        }
                    }
                    if (ok) {
                        newName = Connection.getScreenName(jso);
                        ok = isUsernameValid(newName);
                    }

                    if (ok) {
                        // We are comparing user names ignoring case, but we fix correct case
                        // as the Originating system tells us. 
                        if (!TextUtils.isEmpty(ma.getUsername()) && ma.getUsername().compareToIgnoreCase(newName) != 0) {
                            // Credentials belong to other User ??
                            ok = false;
                            credentialsOfOtherUser = true;
                        }
                    }
                    if (ok) {
                        setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                    }
                    if (ok && !ma.isPersistent()) {
                        // Now we know the name (or proper case of the name) of this User!
                        ok = setUsernameAuthenticated(newName);
                        if (!ok) {
                            errorSettingUsername = true;
                        }
                    }
                    if (!ok) {
                        clearAuthInformation();
                        setCredentialsVerified(CredentialsVerified.FAILED);
                    }
                    // Save the account here
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
        
        public void setAuthInformation(String token, String secret) {
            if(ma.isOAuth()) {
                ConnectionOAuth conn = ((ConnectionOAuth) ma.getConnection());
                conn.setAuthInformation(token, secret);
            } else {
                Log.e(TAG, "saveAuthInformation is for OAuth only!");
            }
        }

        public void setCredentialsVerified(CredentialsVerified cv) {
            ma.mCredentialsVerified = cv;
            if (cv == CredentialsVerified.FAILED) {
               clearAuthInformation(); 
            }
        }

        /**
         * @param oAuth to set
         */
        public void setOAuth(boolean oauth) {
            if (!ma.mOrigin.canChangeOAuth()) {
                oauth = ma.mOrigin.isOAuth();
            }
            if (ma.mOAuth != oauth) {
                setCredentialsVerified(CredentialsVerified.NEVER);
                ma.mOAuth = oauth;
            }
        }

        /**
         * Password was moved to the connection object because it is needed there
         * 
         * @param password
         */
        public void setPassword(String password) {
            if (password.compareTo(ma.getConnection().getPassword()) != 0) {
                setCredentialsVerified(CredentialsVerified.NEVER);
                ma.getConnection().setPassword(password);
            }
        }
        
        /**
         * 1. Set Username for the User who was first time authenticated (and was not saved yet)
         * Remember that the User was ever authenticated
         * 2. Connect this account to the {@link MyDatabase.User} 
         * 
         * @param username - new Username to set.
         */
        private boolean setUsernameAuthenticated(String username) {
            username = fixUsername(username);
            boolean ok = false;

            if (!ma.isPersistent()) {
                // Now we know the name of this User!
                ma.mUsername = username;
                ok = true;
            }
            if (ma.mUserId == 0) {
                ma.mUserId = MyProvider.userNameToId(ma.getOriginId(), username);
                if (ma.mUserId == 0) {
                    DataInserter di = new DataInserter(ma, MyPreferences.getContext(), TimelineTypeEnum.HOME);
                    try {
                        // Construct "User" from available account info
                        // We need this User in order to be able to link Messages to him
                        JSONObject dbUser = new JSONObject();
                        dbUser.put("screen_name", ma.getUsername());
                        dbUser.put(MyDatabase.User.ORIGIN_ID, ma.getOriginId());
                        ma.mUserId = di.insertUserFromJSONObject(dbUser);
                    } catch (Exception e) {
                        Log.e(TAG, "Construct user: " + e.toString());
                    }
                }
            }
            return ok;
        }
        
        @Override
        public int describeContents() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            save();
            // We don't need this until it is persisted
            if (ma.isPersistent()) {
                ma.mUserData.putParcelable(KEY_ACCOUNT, ma.getAccount());
            }
            dest.writeParcelable(ma.mUserData, flags);
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
            return ma.dataContains(key);
        }

        @Override
        public int getDataInt(String key, int defValue) {
            return ma.getDataInt(key, defValue);
        }

        @Override
        public String getDataString(String key, String defValue) {
            return ma.getDataString(key, defValue);
        }
        
        @Override
        public long getOriginId() {
            return ma.getOriginId();
        }

        @Override
        public String getUsername() {
            return ma.getUsername();
        }

        @Override
        public String toString() {
            return ma.toString();
        }
    }
    
    /**
     * Key: Guid of the default account
     */
    public static final String KEY_DEFAULT_ACCOUNT_NAME = "default_account_name";
    /**
     * Guid of the default account
     */
    private static String defaultAccountName = "";
    /**
     * Guid of current account: it is not stored when application is killed
     */
    private static String currentAccountName = "";
    
    /**
     * Prefix of the user's Preferences file
     */
    public static final String FILE_PREFIX = "user_";

    /**
     * Is this account persistent?
     * false - the account was not added (persisted...) yet. This user was not _ever_ authenticated.
     */
    private boolean mIsPersistent = false;
    
    /**
     * This is same name that is used e.g. in Twitter login
     */
    private String mUsername = "";
    
    /**
     * The system in which the User is defined, see {@link Origin}
     */
    private Origin mOrigin; 
    
    /**
     * Android Account associated with this MyAccount
     * Null for NOT Persisted MyAccount
     */
    private android.accounts.Account mAccount;
    
    /**
     * User Data associated with this Account
     * It's mainly used when the MyAccount is not Persisted yet.
     */
    private Bundle mUserData = new Bundle();
    
    /**
     * Id in the database, see {@link MyDatabase.User#_ID}
     */
    private long mUserId = 0;

    /**
     * Was this user authenticated last time _current_ credentials were verified?
     * CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private CredentialsVerified mCredentialsVerified = CredentialsVerified.NEVER;

    /**
     * Is this user authenticated with OAuth?
     */
    private boolean mOAuth = true;

    /**
     * NEVER - means that User was never successfully authenticated with current credentials,
     *      this is why we reset to state to NEVER every time credentials were changed.
     *      TODO: Use instance fields instead of ordinals (see [EffectiveJava] Item 31)
     */
    public enum CredentialsVerified {
        NEVER, FAILED, SUCCEEDED;

        /*
         * Methods to persist in SharedPreferences
         */
        private static final String KEY = "credentials_verified";

        public static CredentialsVerified load(SharedPreferences sp) {
            int ind = sp.getInt(KEY, NEVER.ordinal());
            CredentialsVerified cv = CredentialsVerified.values()[ind];
            return cv;
        }
        
        public static CredentialsVerified load(AccountDataReader dr) {
            int ind = dr.getDataInt(KEY, NEVER.ordinal());
            CredentialsVerified cv = CredentialsVerified.values()[ind];
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
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = Long.parseLong(str);
            }
        } catch (Exception e) {}
        return value;
    }

    private boolean getDataBoolean(String key, boolean defValue) {
        boolean value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = (SharedPreferencesUtil.isTrue(str) != 0);
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
            if (getAccount() == null) {
                Log.e(TAG, "getDataString key=" + key + "; Account is null ");
                return null;
            }
            android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
            String str = am.getUserData(getAccount(), key);
            if (!TextUtils.isEmpty(str)) {
                value = str;
            }
            // And cache retrieved value (Do we really need this?)
            if (TextUtils.isEmpty(value)) {
                mUserData.remove(key);
            } else {
                mUserData.putString(key, value);
            }
        } else {
            String str = mUserData.getString(key);
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
        return getConnection().getCredentialsPresent(this);
    }    
    
    public CredentialsVerified getCredentialsVerified() {
        return mCredentialsVerified;
    }
    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     */
    public static void forget() {
        mMyAccounts = null;
    }
    
    /**
     * Get instance of current MyAccount (MyAccount selected by the user). The account isPersistent
     * 
     * @param Context
     * @return MyAccount or null if no persistent accounts exist
     */
    public static MyAccount getCurrentMyAccount() {
        MyAccount ma = null;
        int ind = indexOfMyAccount(currentAccountName);
        if (ind < 0) {
            currentAccountName = "";
            ind = indexOfMyAccount(defaultAccountName);
            if (ind < 0) {
                defaultAccountName = "";
            }
        }
        if (ind < 0) {
            if (mMyAccounts.size() > 0) {
                ind = 0;
            }
        }
        if (ind >= 0) {
            ma = mMyAccounts.elementAt(ind);
            if (ma.isPersistent()) {
                // Correct Current and Default Accounts if needed
                if (TextUtils.isEmpty(currentAccountName)) {
                    setCurrentMyAccountGuid(ma.getAccountGuid());
                }
                if (TextUtils.isEmpty(defaultAccountName)) {
                    setDefaultMyAccountGuid(ma.getAccountGuid());
                }
            } else {
                // Return null if the account appeared to be not persistent
                ma = null;
            }
        }
        return ma;
    }
    
    /**
     * Get Guid of current MyAccount (MyAccount selected by the user). The account isPersistent
     * 
     * @param Context
     * @return Account GUID or empty string if no persistent accounts exist
     */
    public static String getCurrentMyAccountGuid() {
        MyAccount ma = getCurrentMyAccount();
        if (ma != null) {
            return ma.getAccountGuid();
        } else {
            return "";
        }
    }
    
    /**
     * @return 0 if no current account
     */
    public static long getCurrentMyAccountUserId() {
        long userId = 0;
        if (getCurrentMyAccount() != null) {
            userId = getCurrentMyAccount().getUserId();
        }
        return userId;
    }
    
    /** 
     * Array of MyAccount objects
     */
    private static Vector<MyAccount> mMyAccounts = null;
    
    /**
     * Get list of all Users, including temporary (never authenticated) user
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link #getCredentialsVerified()} is the main differentiator.
     * 
     * @param context
     * @return Array of users
     */
    public static MyAccount[] list() {
        if (mMyAccounts == null) {
            Log.e(TAG, "Was not initialized");
            return null;
        } else {
            return mMyAccounts.toArray(new MyAccount[mMyAccounts.size()]);
        }
    }
    
    /**
     * How many authenticated users are the list of accounts?
     * @return count
     */
    public static int countOfAuthenticatedUsers() {
        int count = 0;
        int ind = -1;

        for (ind = 0; ind < mMyAccounts.size(); ind++) {
            if (mMyAccounts.elementAt(ind).isPersistent()) {
                count += 1;
            }
        }
        return count;
    }

    
    /**
     * Are authenticated users from more than one different Originating system?
     * @return count
     */
    public static boolean moreThanOneOriginatingSystem() {
        int count = 0;
        long originId = 0;
        int ind = -1;

        for (ind = 0; ind < mMyAccounts.size(); ind++) {
            if (mMyAccounts.elementAt(ind).isPersistent()) {
                if (originId != mMyAccounts.elementAt(ind).getOriginId() ) {
                    count += 1;
                    originId = mMyAccounts.elementAt(ind).getOriginId();
                    if (count >1) {
                        break;
                    }
                }
            }
        }
        return (count>1);
    }
    
    /**
     * Initialize internal static memory 
     * Initialize User's list if it wasn't initialized yet.
     * 
     * @param context
     */
    public static void initialize() {
        if (mMyAccounts == null) {
            mMyAccounts = new Vector<MyAccount>();
            defaultAccountName = MyPreferences.getDefaultSharedPreferences().getString(KEY_DEFAULT_ACCOUNT_NAME, "");

            android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
            android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
            for (int ind = 0; ind < aa.length; ind++) {
                Builder mab = new Builder(aa[ind]);
                mMyAccounts.add(mab.getMyAccount());
            }
            MyLog.v(TAG, "Account list initialized, " + mMyAccounts.size() + " accounts");
        }
        else {
            MyLog.v(TAG, "Already initialized, " + mMyAccounts.size() + " accounts");
        }
    }

    /**
     * Name of preferences file for this MyAccount
     * @return Name without path and extension
     */
    private String prefsFileName() {
        String fileName = FILE_PREFIX + getAccountGuid().replace("/", "-");
        return fileName;
    }

    /**
     * Get MyAccount by its Id
     * @param accountId
     * @return null if not found
     */
    public static MyAccount getMyAccount(long accountId) {
        boolean found = false;
        MyAccount ma = null;
        for (int ind = 0; ind < mMyAccounts.size(); ind++) {
            if (mMyAccounts.elementAt(ind).getUserId() == accountId) {
                found = true;
                ma = mMyAccounts.elementAt(ind);
                break;
            }
        }
        if (!found) { ma = null;}
        return ma;
    }

    
    /**
     * Factory of MyAccount-s
     * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
     * 
     * @param accountName - AccountGuid (in the form of systemname/username - with slash "/" between them)
     *  if accountName doesn't have systemname (before slash), default system name is assumed
     * @return MyAccount - existed or newly created. For new MyAccount we assume that it is not persistent.
     */
    public static MyAccount getMyAccount(String accountName) {
        int ind = indexOfMyAccount(accountName);
        MyAccount ma = null;
        if (ind < 0) {
            // Create temporary MyAccount
            ma = new Builder(accountName).getMyAccount();
        } else {
            ma = mMyAccounts.elementAt(ind);
        }
        return ma;
    }

    /**
     * Return first found MyAccount with provided originId
     * @param originId
     * @return null if not found
     */
    private static MyAccount findFirstMyAccountByOriginId(long originId) {
        boolean found = false;
        MyAccount ma = null;
        for (int ind = 0; ind < mMyAccounts.size(); ind++) {
            if (mMyAccounts.elementAt(ind).getOriginId() == originId) {
                found = true;
                ma = mMyAccounts.elementAt(ind);
                break;
            }
        }
        if (!found) { ma = null;}
        return ma;
    }
    
    /**
     * Find account of the User linked to this message, 
     * or other appropriate account in a case the User is not an Account.
     * For any action with the message we should choose an Account 
     * from the same originating (source) System.
     * @param msgId  Message ID
     * @param userId The message is in his timeline. 0 if the message doesn't belong to any timeline
     * @param myAccountUserId Preferred account (or 0), used in a case userId is not an Account 
     *          or is not linked to the message 
     * @return null if nothing suitable found
     */
    public static MyAccount getMyAccountLinkedToThisMessage(long msgId, long userId, long myAccountUserId)
    {
        MyAccount ma = getMyAccount(userId);
        if (msgId == 0 || ma == null) {
            ma = getMyAccount(myAccountUserId);
        }
        long originId = MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.ORIGIN_ID, msgId);
        if (ma == null || originId != ma.getOriginId()) {
           ma = findFirstMyAccountByOriginId(originId); 
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "getMyAccountLinkedToThisMessage msgId=" + msgId +"; userId=" + userId 
                    + " -> account=" + (ma==null ? "null" : ma.getAccountGuid()));
        }
        return ma;
    }
    
    /**
     * Find MyAccount by accountName in local cache AND in Android AccountManager
     * @param accountName
     * @return -1 if was not found or index in mMyAccounts
     */
    private static int indexOfMyAccount(String accountName) {
        boolean found = false;
        int indReturn = -1;

        accountName = fixAccountName(accountName);
        
        // This is Guid
        for (int ind = 0; ind < mMyAccounts.size(); ind++) {
            if (mMyAccounts.elementAt(ind).getAccountGuid().compareTo(accountName) == 0) {
                found = true;
                indReturn = ind;
                // MyService.v(TAG, "User '" + tu.getUsername() + "' was found");
                break;
            }
        }
        if (!found) {
            // Try to find persisted Account which was not loaded yet
            if (!TextUtils.isEmpty(accountName)) {
                android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
                android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
                for (int ind = 0; ind < aa.length; ind++) {
                  if (aa[ind].name.compareTo(accountName)==0) {
                      found = true;
                      MyAccount ma = new Builder(aa[ind]).getMyAccount();
                      mMyAccounts.add(ma);
                      indReturn = mMyAccounts.size() - 1;
                      break;
                  }
                }
                if (found) {
                    // It looks like preferences has changed...
                    MyPreferences.setPreferencesChangedNow();
                }
            }
        }
        return indReturn;
    }
    
    /**
     * Delete everything about the MyAccount
     * 
     * @return Was the MyAccount (and Account) deleted?
     */
    public static boolean removeMyAccount(MyAccount ma) {
        boolean isDeleted = false;

        if (mMyAccounts == null) {
            Log.e(TAG, "delete: Was not initialized.");
        } else {
            // Delete the User's object from the list
            int ind = -1;
            boolean found = false;
            for (ind = 0; ind < mMyAccounts.size(); ind++) {
                if (mMyAccounts.elementAt(ind).equals(ma)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                new Builder(ma).deleteData();

                // And delete the object from the list
                mMyAccounts.removeElementAt(ind);

                isDeleted = true;
                MyPreferences.setPreferencesChangedNow();
            }
        }
        return isDeleted;
    }

    private static String fixAccountName(String accountName) {
        accountName = fixUsername(accountName);
        if (accountName.indexOf("/") < 0) {
            accountName = Origin.ORIGIN_NAME_TWITTER + "/" + accountName;   
           }
        return accountName;
    }
    
    private static String fixUsername(String username) {
        if (username == null) {
            username = "";
        }
        username = username.trim();
        if (!isUsernameValid(username)) {
            username = "";
        }
        return username;
    }

    private static String accountNameToUsername(String accountName) {
        accountName = fixAccountName(accountName);
        int indSlash = accountName.indexOf("/");
        String userName = "";
        if (indSlash >= 0) {
            if (indSlash < accountName.length()-1) {
                userName = accountName.substring(indSlash + 1);
            }
        } else {
            userName = accountName;
        }
        return fixUsername(userName);
    }

    private static String accountNameToOriginName(String accountName) {
        accountName = fixAccountName(accountName);
        int indSlash = accountName.indexOf("/");
        String originName = Origin.ORIGIN_NAME_TWITTER;
        if (indSlash >= 0) {
            originName = accountName.substring(0, indSlash);
        }
        return originName;
    }
    
    /**
     * Set current MyAccount to 'this' object.
     * Current account selection is not persistent
     */
    public static synchronized void setCurrentMyAccountGuid(String accountGuid) {
        currentAccountName = accountGuid;
    }

    /**
     * Set this MyAccount as a default one.
     * Default account selection is persistent
     */
    public static synchronized void setDefaultMyAccountGuid(String accountGuid) {
        defaultAccountName = accountGuid;
        MyPreferences.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
    }
    
    private MyAccount() {};

    /**
     * @return the mUsername
     */
    @Override
    public String getUsername() {
        return mUsername;
    }

    /**
     * @return Account associated with this MyAccount. Null if MyAccount is not persistent yet
     */
    public android.accounts.Account getAccount() {
        if (!isPersistent() || mAccount == null) {
            // Recreate the object for the case something changed
            mAccount = new android.accounts.Account(getAccountGuid(), AuthenticatorService.ANDROID_ACCOUNT_TYPE);
        }
        return mAccount;
    }
    
    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id 
     */
    public String getAccountGuid() {
        return mOrigin.getName() + "/" + getUsername();
    }
    
    
    /**
     * @return the {@link #mUserId}
     */
    public long getUserId() {
        return mUserId;
    }

    /**
     * @return id of the system in which the User is defined, see {@link MyDatabase.User#ORIGIN_ID}
     */
    @Override
    public long getOriginId() {
        return mOrigin.getId();
    }
    
    /**
     * @return Name of the system in which the User is defined
     */
    public String getOriginName() {
        return mOrigin.getName();
    }
    
    /**
     * @return SharedPreferences of this MyAccount. Used to store preferences which are application specific
     *   i.e. excluding data specific to Account. 
     */
    public SharedPreferences getMyAccountPreferences() {
        SharedPreferences sp = null;
        String prefsFileName = prefsFileName();
        
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

    /**
     * @return Is this object persistent 
     */
    public boolean isPersistent() {
        return mIsPersistent;
    }

    private static boolean isUsernameValid(String username) {
        boolean ok = false;
        if (username != null && (username.length() > 0)) {
            ok = Pattern.matches("[a-zA-Z_0-9/\\.\\-\\(\\)]+", username);
            if (!ok && MyLog.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "The Username is not valid: \"" + username + "\"");
            }
        }
        return ok;
    }

    /**
     * @return instance of Connection subtype for the User
     */
    public Connection getConnection() {
        return mOrigin.getConnection(this, mOAuth);
    }
    
    /**
     * @return Implementation of the {@link MyOAuth} interface
     */
    public MyOAuth getOAuth() {
        MyOAuth oa = null;
        if (isOAuth()) {
            oa = (MyOAuth) getConnection();
        }
        return oa;
    }
    

    /**
     * Calculates number of Characters left for this message
     * @param message
     * @return
     */
    public int messageCharactersLeft(String message) {
        return mOrigin.messageCharactersLeft(message);
    }

    /**
     * {@link Origin#alternativeTermResourceId(int)}
     */
    public int alternativeTermResourceId(int resId) {
        return mOrigin.alternativeTermResourceId(resId);
    }
    
    /**
     * {@link Origin#messagePermalink(String, String)}
     */
    public String messagePermalink(String userName, String messageOid) {
        return mOrigin.messagePermalink(userName, messageOid);
    }

    /**
     * @return the mOAuth
     */
    public boolean isOAuth() {
        return mOAuth;
    }

    public String getPassword() {
        return getConnection().getPassword();
    }
    
    /**
     * This is defined by tweet server
     * Starting from 2010-09 twitter.com allows OAuth only
     */
    public boolean canChangeOAuth() {
        return mOrigin.canChangeOAuth();
    }
    
    /**
     * Can user set username for the new user manually?
     * Current implementation of twitter.com authentication doesn't use this attribute, so it's disabled
     */
    public boolean canSetUsername() {
        return mOrigin.canSetUsername(isOAuth());
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String str = TAG;
        String members = getAccountGuid();
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
