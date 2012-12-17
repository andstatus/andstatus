/**
 * Copyright (C) 2010-2012 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.TimelineDownloader;
import org.andstatus.app.account.Origin.OriginApiEnum;
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
 * The object holds MyAccount specific information including 
 * an Instant messaging System (twitter.com, identi.ca etc.), 
 * Username in that system and connection to it.
 * 
 * Current version works with twitter.com only!
 * 
 * @author Yuri Volkov
 */
public class MyAccount implements Parcelable {
    private static final String TAG = MyAccount.class.getSimpleName();

    /**
     * Key: Guid of the default account
     */
    public static final String KEY_DEFAULT_ACCOUNT_NAME = "default_account_name";
    /**
     * Guid of the default account
     */
    public static String defaultAccountName = "";
    /**
     * Guid of current account: it is not stored when application is killed
     */
    public static String currentAccountName = "";
    
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

    //------------------------------------------------------------
    // MyAccount preferences are below:
    
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
    public static final String KEY_PASSWORD = "password";
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
     * Id of the last message downloaded from this timeline type and for this MyAccount
     * for actual key name append timelinetypeid to this key 
     */
    public static final String KEY_LAST_TIMELINE_ID = "last_timeline_id_";

    /**
     * NEVER - means that User was never successfully authenticated with current credentials,
     *      this is why we reset to state to NEVER every time credentials were changed.
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
        
        public static CredentialsVerified load(MyAccount ma) {
            int ind = ma.getDataInt(KEY, NEVER.ordinal());
            CredentialsVerified cv = CredentialsVerified.values()[ind];
            return cv;
        }
        
        public void save(SharedPreferences sp) {
            synchronized (sp) {
                SharedPreferences.Editor editor = sp.edit();
                put(editor);
                editor.commit();
            }
        }

        public void put(SharedPreferences.Editor editor) {
            editor.putInt(KEY, ordinal());
        }

        public void put(MyAccount ma) {
            ma.setDataInt(KEY, ordinal());
        }
    }

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

    public long getDataLong(String key, long defValue) {
        long value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = Long.parseLong(str);
            }
        } catch (Exception e) {}
        return value;
    }

    public boolean getDataBoolean(String key, boolean defValue) {
        boolean value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = (MyPreferences.isTrue(str) != 0);
            }
        } catch (Exception e) {}
        return value;
    }

    /**
     * User Data associated with the account
     */
    public String getDataString(String key, String defValue) {
        String value = defValue;
        if (isPersistent()) {
            if (getAccount() == null) {
                Log.e(TAG, "getDataString key=" + key + "; mAccount is null ");
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

    public void setDataString(String key, String value) {
        try {
            if (TextUtils.isEmpty(value)) {
                mUserData.remove(key);
            } else {
                mUserData.putString(key, value);
            }
            if (isPersistent()) {
                if (getAccount() == null) {
                    Log.e(TAG, "setDataString key=" + key + "; mAccount is null ");
                    return;
                }
                android.accounts.AccountManager am = AccountManager.get(MyPreferences.getContext());
                am.setUserData(getAccount(), key, value);
            }
        } catch (Exception e) {}
    }

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
    
    public boolean getCredentialsPresent() {
        return getConnection().getCredentialsPresent(this);
    }    
    
    public CredentialsVerified getCredentialsVerified() {
        return mCredentialsVerified;
    }

    public void setCredentialsVerified(CredentialsVerified cv) {
        mCredentialsVerified = cv;
        if (cv == CredentialsVerified.FAILED) {
           clearAuthInformation(); 
        }
    }

    public void setAuthInformation(String token, String secret) {
        if(isOAuth()) {
            ConnectionOAuth conn = ((ConnectionOAuth) getConnection());
            conn.setAuthInformation(token, secret);
        } else {
            Log.e(TAG, "saveAuthInformation is for OAuth only!");
        }
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
            // Correct Current and Default Accounts if needed
            if (TextUtils.isEmpty(currentAccountName)) {
                ma.setCurrentMyAccount();
            }
            if (TextUtils.isEmpty(defaultAccountName)) {
                ma.setDefaultMyAccount();
            }
        }

        return ma;
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
                break;
            }
        }
        return count;
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
                MyAccount tu = new MyAccount(aa[ind], true);
                mMyAccounts.add(tu);
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
     * For any action with the message we should choose an Account 
     * from the same originating (source) System
     * @param systemId  Message ID, 0 for the message creation
     * @param userId User ID in the timeline, 0 if the message doesn't belong to any timeline
     * @return null if not found
     */
    public static MyAccount getMyAccountForTheMessage(long systemId, long userId)
    {
        MyAccount ma = null;
        if (systemId == 0) {
            ma = getCurrentMyAccount();
        } else {
            ma = getMyAccount(userId);
            if (ma == null) {
                ma = getCurrentMyAccount();
            }
            long originId = MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.ORIGIN_ID, systemId);
            if ( originId != ma.getOriginId()) {
               ma = findFirstMyAccountByOriginId(originId); 
            }
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "getMyAccountForTheMessage systemId=" + systemId +"; userId=" + userId 
                    + "; account=" + (ma==null ? "null" : ma.getAccountGuid()));
        }
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
        int ind = -1;
        MyAccount ma = null;

        accountName = fixAccountName(accountName);
        ind = indexOfMyAccount(accountName);
        if (ind < 0) {
            removeNotPersistentMyAccounts();
            // Create temporary MyAccount
            ma = new MyAccount(accountName);
            mMyAccounts.add(ma);
        } else {
            ma = mMyAccounts.elementAt(ind);
        }
        return ma;
    }

    /**
     * Factory of MyAccount-s
     * If MyAccount for this Account didn't exist yet, new temporary MyAccount will be created.
     * 
     * @param account If it's null, new MyAccount with empty username will be created
     * @param isPersistent true if this account is persistent already
     * @return MyAccount - existed or newly created.
     */
    public static MyAccount getMyAccount(android.accounts.Account account, boolean isPersistent) {
        MyAccount ma = null;
        if (account == null) {
           ma = getMyAccount("");
        } else {
            int ind = -1;
            String accountName = fixAccountName(account.name);
            if (accountName.compareTo(account.name) != 0) {
                Log.e(TAG,"Invalid persistent account.name: '" + account.name + "'");
            }
            ind = indexOfMyAccount(accountName);
            if (ind < 0) {
                removeNotPersistentMyAccounts();
                ma = new MyAccount(account, isPersistent);
                mMyAccounts.add(ma);
            } else {
                ma = mMyAccounts.elementAt(ind);
            }
        }
        return ma;
    }
    
    /**
     * Find MyAccount in mMyAccounts by accountName
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
                      MyAccount ma = new MyAccount(aa[ind], true);
                      mMyAccounts.add(ma);
                      indReturn = mMyAccounts.size() - 1;
                      break;
                  }
                }
            }
        }
        return indReturn;
    }

    /**
     * Remove all not persistent MyAccounts from mMyAccounts
     */
    private static void removeNotPersistentMyAccounts() {
        for (int ind = mMyAccounts.size() - 1 ; ind >= 0; ind--) {
            if (!mMyAccounts.elementAt(ind).isPersistent()) {
                // Simply delete the object from the list
                mMyAccounts.removeElementAt(ind);
            }
        }
    }
    
    /**
     * Delete everything about the MeAccount
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
                ma.deleteData();

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
     * Creates new account, which is not Persistent yet
     * @param accountName
     */
    private MyAccount(String accountName) {
        mOrigin = Origin.getOrigin(accountNameToOriginName(accountName));
        mUsername = accountNameToUsername(accountName);
        mOAuth = mOrigin.isOAuth();
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "New temporary account created: " + this.toString());
        }
    }

    /**
     * Loads existing account from Persistence or from mUserData in a case the account is not persistent 
     * @param account should not be null
     * @param isPersistent true for Persistent account
     */
    private MyAccount(android.accounts.Account account, boolean isPersistent) {
        if (account == null) {
            throw new IllegalArgumentException(TAG + " null account is not allowed the constructor");
        }
        mAccount = account;
        mIsPersistent = isPersistent;

        mOrigin = Origin.getOrigin(accountNameToOriginName(getAccount().name));
        mUsername = accountNameToUsername(getAccount().name);
        
        // Load stored data for the User
        mCredentialsVerified = CredentialsVerified.load(this);
        mOAuth = getDataBoolean(MyAccount.KEY_OAUTH, mOrigin.isOAuth());
        mUserId = getDataLong(MyAccount.KEY_USER_ID, 0L);
        
        if (mUserId==0) {
            setUsernameAuthenticated(mUsername);
            Log.e(TAG, "MyAccount '" + getUsername() + "' was not connected to the User table. UserId=" + mUserId);
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Loaded persistent " + this.toString());
        }
    }

    /**
     * Loads existing account from Persistence or from mUserData in a case the account is not persistent 
     * @param account should not be null
     */
    private void loadMyAccount(android.accounts.Account account, boolean isPersistent) {
        mIsPersistent = isPersistent;
        if (isPersistent() && account == null) {
            throw new IllegalArgumentException(TAG + " null account for persistent MyAccount is not allowed");
        }
        mAccount = account;

        String originName = Origin.ORIGIN_NAME_TWITTER;
        String userName = "";
        if (isPersistent()) {
            originName = accountNameToOriginName(getAccount().name);
            userName = accountNameToUsername(getAccount().name);
        } else {
            originName = getDataString(MyAccount.KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER);
            userName = getDataString(MyAccount.KEY_USERNAME, "");
        }
        mOrigin = Origin.getOrigin(originName);
        mUsername = fixUsername(userName);
        
        // Load stored data for the MyAccount
        mCredentialsVerified = CredentialsVerified.load(this);
        mOAuth = getDataBoolean(MyAccount.KEY_OAUTH, mOrigin.isOAuth());
        mUserId = getDataLong(MyAccount.KEY_USER_ID, 0L);
        
        if (mUserId==0) {
            setUsernameAuthenticated(mUsername);
            Log.e(TAG, "MyAccount '" + getUsername() + "' was not connected to the User table. UserId=" + mUserId);
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Loaded persistent " + this.toString());
        }
    }
    
    private MyAccount(Parcel source) {
        boolean isPersistent = getDataBoolean(KEY_PERSISTENT, false);
        mUserData = source.readBundle();
        
        // Load as if the account is not persisted to force loading everything from mUserData
        loadMyAccount(null, false);

        // Do this as a last step
        if (isPersistent) {
            mAccount = mUserData.getParcelable(KEY_ACCOUNT);
            if (mAccount == null) {
                isPersistent = false;
                Log.e(TAG, "The account was marked as persistent:" + this);
            }
            mIsPersistent = isPersistent;
        }
    }

    /**
     * @return the mUsername
     */
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
    public long getOriginId() {
        return mOrigin.getId();
    }

    /**
     * @return API of the Originating system
     */
    public OriginApiEnum getApi() {
        return mOrigin.getApi();
    }
    
    /**
     * @return Name of the system in which the User is defined
     */
    public String getOriginName() {
        return mOrigin.getName();
    }
    
    /**
     * @return Base URL for connection to the System
     */
    public String getBaseUrl() {
        return mOrigin.getBaseUrl();
    }
    
    /**
     * @return Base URL for OAuth related requests to the System
     */
    public String getOauthBaseUrl() {
        return mOrigin.getOauthBaseUrl();
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
     * 1. Set Username for the User who was first time authenticated (and was not saved yet)
     * Remember that the User was ever authenticated
     * 2. Connect this account to the {@link MyDatabase.User} 
     * 
     * @param username - new Username to set.
     */
    private boolean setUsernameAuthenticated(String username) {
        username = fixUsername(username);
        boolean ok = false;

        if (!isPersistent()) {
            // Now we know the name of this User!
            mUsername = username;
            ok = true;
        }
        if (mUserId == 0) {
            mUserId = MyProvider.userNameToId(getOriginId(), username);
            if (mUserId == 0) {
                TimelineDownloader td = new TimelineDownloader(this, MyPreferences.getContext(), TimelineTypeEnum.HOME);
                try {
                    // Construct "User" from available account info
                    // We need this User in order to be able to link Messages to him
                    JSONObject dbUser = new JSONObject();
                    dbUser.put("screen_name", getUsername());
                    dbUser.put(MyDatabase.User.ORIGIN_ID, getOriginId());
                    mUserId = td.insertUserFromJSONObject(dbUser);
                } catch (Exception e) {
                    Log.e(TAG, "Construct user: " + e.toString());
                }
            }
        }
        return ok;
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
            if (!isPersistent() && (getCredentialsVerified() == CredentialsVerified.SUCCEEDED)) {
                try {
                    // Now add this account to account manager
                    // See {@link com.android.email.provider.EmailProvider.createAccountManagerAccount(Context, String, String)}
                    AccountManager accountManager = AccountManager.get(MyPreferences.getContext());

                    /* Note: We could add userdata from {@link mUserData} Bundle, 
                     * but we decided to add it below one by one item
                     */
                    accountManager.addAccountExplicitly(getAccount(), getPassword(), null);
                    // Immediately mark as persistent
                    mIsPersistent = true;
                    
                    // TODO: This is not enough, we need "sync adapter":
                    // SyncManager(865): can't find a sync adapter for SyncAdapterType Key 
                    // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app}, removing settings for it
                    ContentResolver.setIsSyncable(getAccount(), MyProvider.AUTHORITY, 1);
                    ContentResolver.setSyncAutomatically(getAccount(), MyProvider.AUTHORITY, true);
                    
                    MyLog.v(TAG, "Persisted " + getAccountGuid());
                } catch (Exception e) {
                    Log.e(TAG, "Adding Account to AccountManager: " + e.getMessage());
                }
            }
            
            if (getDataString(KEY_USERNAME, "").compareTo(mUsername) !=0 ) {
                setDataString(MyAccount.KEY_USERNAME, mUsername);
                changed = true;
            }
            if (mOrigin.getName().compareTo(getDataString(KEY_ORIGIN_NAME, Origin.ORIGIN_NAME_TWITTER)) != 0) {
                setDataString(KEY_ORIGIN_NAME, mOrigin.getName());
                changed = true;
            }
            if (mCredentialsVerified != CredentialsVerified.load(this)) {
                mCredentialsVerified.put(this);
                changed = true;
            }
            if (mOAuth != getDataBoolean(MyAccount.KEY_OAUTH, mOrigin.isOAuth())) {
                setDataBoolean(MyAccount.KEY_OAUTH, mOAuth);
                changed = true;
            }
            if (mUserId != getDataLong(MyAccount.KEY_USER_ID, 0L)) {
                setDataLong(MyAccount.KEY_USER_ID, mUserId);
                changed = true;
            }
            if (getConnection().save(this)) {
                changed = true;
            }
            if (mIsPersistent != getDataBoolean(MyAccount.KEY_PERSISTENT, false)) {
                setDataBoolean(MyAccount.KEY_PERSISTENT, mIsPersistent);
                changed = true;
            }

            if (changed && isPersistent()) {
                MyPreferences.setPreferencesChangedNow();
            }

            MyLog.v(TAG, "Saved " + (changed ? " changed " : " no changes " ) + this);
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, "saving " + getAccountGuid() + ": " + e.toString());
            e.printStackTrace();
            ok = false;
        }
        return ok;
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
     * Delete all User's data
     * @return true = success 
     */
    private boolean deleteData() {
        boolean ok = true;

        // Old preferences file may be deleted, if it exists...
        ok = SharedPreferencesUtil.delete(MyPreferences.getContext(), prefsFileName());

        if (isPersistent()) {
            if (mUserId != 0) {
                // TODO: Delete databases for this User
                
                mUserId = 0;
            }

            // We don't delete Account from Account Manager here
            mAccount = null;
            mIsPersistent = false;
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
     * Clear Authentication information
     * 
     * @param context
     */
    public void clearAuthInformation() {
        setCredentialsVerified(CredentialsVerified.NEVER);
        this.getConnection().clearAuthInformation();
    }

    /**
     * @return the mOAuth
     */
    public boolean isOAuth() {
        return mOAuth;
    }

    /**
     * @param oAuth to set
     */
    public void setOAuth(boolean oauth) {
        if (!mOrigin.canChangeOAuth()) {
            oauth = mOrigin.isOAuth();
        }
        if (mOAuth != oauth) {
            setCredentialsVerified(CredentialsVerified.NEVER);
            mOAuth = oauth;
        }
    }

    /**
     * Password was moved to the connection object because it is needed there
     * 
     * @param password
     */
    public void setPassword(String password) {
        if (password.compareTo(getConnection().getPassword()) != 0) {
            setCredentialsVerified(CredentialsVerified.NEVER);
            getConnection().setPassword(password);
        }
    }

    public String getPassword() {
        return getConnection().getPassword();
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
            if (getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
                ok = true;
            }
        }
        if (!ok) {
            JSONObject jso = null;
            try {
                jso = getConnection().verifyCredentials();
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
                    if (!TextUtils.isEmpty(getUsername()) && getUsername().compareToIgnoreCase(newName) != 0) {
                        // Credentials belong to other User ??
                        ok = false;
                        credentialsOfOtherUser = true;
                    }
                }
                if (ok) {
                    setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                }
                if (ok && !isPersistent()) {
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

    /**
     * Set current MyAccount to 'this' object.
     * Current account selection is not persistent
     */
    public synchronized void setCurrentMyAccount() {
        currentAccountName = getAccountGuid();
    }

    /**
     * Set this MyAccount as a default one.
     * Default account selection is persistent
     */
    public synchronized void setDefaultMyAccount() {
        defaultAccountName = getAccountGuid();
        MyPreferences.getDefaultSharedPreferences().edit()
                .putString(KEY_DEFAULT_ACCOUNT_NAME, defaultAccountName).commit();
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
        String str = super.toString();
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

    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        save();
        // We don't need this until it is persisted
        if (isPersistent()) {
            mUserData.putParcelable(KEY_ACCOUNT, getAccount());
        }
        dest.writeParcelable(mUserData, flags);
    }
    
    public static final Creator<MyAccount> CREATOR = new Creator<MyAccount>() {
        public MyAccount createFromParcel(Parcel source) {
            return new MyAccount(source);
        }

        public MyAccount[] newArray(int size) {
            return new MyAccount[size];
        }
    };
    
}
