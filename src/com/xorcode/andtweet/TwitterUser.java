/**
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
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

package com.xorcode.andtweet;

import static android.content.Context.MODE_PRIVATE;
import static com.xorcode.andtweet.PreferencesActivity.KEY_TWITTER_USERNAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera.PreviewCallback;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.SocketTimeoutException;
import java.util.regex.Pattern;
import java.util.Vector;

import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionBasicAuth;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;
import com.xorcode.andtweet.util.SharedPreferencesUtil;

import org.json.JSONObject;

/**
 * The object holds Twitter User's specific information including connection
 * TODO: Impement different data (tweets and their counters...) for different Users.
 * 
 * @author Yuri Volkov
 */
public class TwitterUser {
    private static final String TAG = TwitterUser.class.getSimpleName();

    private Context mContext;

    /**
     * This is same name that is used in Twitter login
     */
    private String mUsername = "";

    /**
     * Was this user ever authenticated?
     */
    private boolean mWasAuthenticated = false;

    /**
     * Was this user authenticated last time credentials were verified?
     * CredentialsVerified.NEVER - after changes of password/OAuth...
     */
    private CredentialsVerified mCredentialsVerified = CredentialsVerified.NEVER;

    private String mPrefsFileName = "";

    /**
     * TODO: These preferences will be per User
     */
    protected SharedPreferences mSp;

    /**
     * Is this user authenticated with OAuth?
     */
    private boolean mOAuth;

    private String mPassword;

    private Connection mConnection;

    public enum CredentialsVerified {
        NEVER, FAILED, SUCCEEDED;

        /*
         * Methods to persist in SharedPreferences
         */
        private static final String KEY = "credentials_verified";

        public static CredentialsVerified load(SharedPreferences sp) {
            return values()[sp.getInt(KEY, NEVER.ordinal())];
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
    }

    public CredentialsVerified getCredentialsVerified() {
        return mCredentialsVerified;
    }

    /**
     * Do we have enough credentials to verify them?
     * 
     * @return true == yes
     */
    public boolean getCredentialsPresent() {
        return getConnection().getCredentialsPresent();
    }

    public String getPassword() {
        return mPassword;
    }

    public void setCredentialsVerified(CredentialsVerified cv) {
        mCredentialsVerified = cv;
        mCredentialsVerified.save(mSp);
        if (!mWasAuthenticated
                && (mCredentialsVerified.compareTo(CredentialsVerified.SUCCEEDED) == 0)) {
            mWasAuthenticated = true;
            mSp.edit().putBoolean(PreferencesActivity.KEY_WAS_AUTHENTICATED, true).commit();
        }
    }

    /**
     * Get user instance based on global twitter_username
     * 
     * @param Context
     * @param copyGlobal globally stored User preferences are used, including
     *            username, oauth, password
     * @return TwitterUser
     */
    public static TwitterUser getTwitterUser(Context context, boolean copyGlobal) {
        return getTwitterUser(context, null, copyGlobal);
    }

    /**
     * Get (stored) user instance based on explicitly provided username.
     * Global SharedPreferences will be updated.
     * 
     * @param Context
     * @param username in Twitter
     * @return TwitterUser
     */
    public static TwitterUser getTwitterUser(Context context, String username) {
        return getTwitterUser(context, username, false);
    }

    // Array of TwitterUser objects
    private static Vector<TwitterUser> mTu = new Vector<TwitterUser>();

    /**
     * Factory of TwitterUser-s
     * 
     * @param Context
     * @param username in Twitter
     * @param copyGlobal globally stored User preferences are used, including
     *            Username, OAuth, password
     * @return TwitterUser
     */
    private static TwitterUser getTwitterUser(Context context, String username, boolean copyGlobal) {
        // Find TwitterUser object for this user
        boolean found = false;
        int ind = -1;
        TwitterUser tu = null;

        if (username == null) {
            username = "";
        }
        username = username.trim();
        if (copyGlobal || (username.length() == 0)) {
            SharedPreferences dsp = PreferenceManager.getDefaultSharedPreferences(context);
            username = dsp.getString(PreferencesActivity.KEY_TWITTER_USERNAME, "");
        }
        if (!isUsernameValid(username)) {
            // We need the object anyway, so let's put empty name
            username = "";
        }
        for (ind=0; ind<mTu.size(); ind++) {
            if (mTu.elementAt(ind).getUsername().compareTo(username) == 0) {
                found = true;
                break;
            }
            
        }
        if (found) {
            tu = mTu.elementAt(ind);
            if (copyGlobal) {
                tu.copyGlobal();
            }
        } else {
            tu = new TwitterUser(context, username, copyGlobal);
            mTu.add(tu);
        }
        return tu;
    }

    private TwitterUser(Context context, String username, boolean copyGlobal) {
        mContext = context;
        setUsername(username, copyGlobal);
    }

    /**
     * @return the mUsername
     */
    public String getUsername() {
        return mUsername;
    }

    /**
     * @param username the Username to set.
     * @param copyGlobal - Set User's data according to the Global (Default)
     *            Shared properties
     */
    private void setUsername(String username, boolean copyGlobal) {
        if (username == null) {
            username = "";
        }
        username = username.trim();
        SharedPreferences dsp = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (copyGlobal || (username.length() == 0)) {
            username = dsp.getString(PreferencesActivity.KEY_TWITTER_USERNAME, "");
        }
        if (!isUsernameValid(username)) {
            username = getUsername();
            if ((username.length() > 0) && !isUsernameValid(username)) {
                username = "";
            }
        }
        boolean newUser = (mSp == null || (mUsername.compareTo(username) != 0));

        if (copyGlobal || newUser) {
            mConnection = null;
            if (newUser) {
                mSp = null;
                if (mUsername.length() > 0) {
                    // Delete data for users that were not authenticated
                    deleteData(true);
                }

                mUsername = username;
                mPrefsFileName = "user_" + mUsername;
                mSp = mContext.getSharedPreferences(mPrefsFileName, MODE_PRIVATE);
                // Load stored data for other user
                boolean isNewFile = mSp.getString(PreferencesActivity.KEY_TWITTER_USERNAME,
                        "(not set)").compareTo(mUsername) != 0;
                if (isNewFile) {
                    mSp.edit().putString(PreferencesActivity.KEY_TWITTER_USERNAME, mUsername)
                            .commit();
                }

                mWasAuthenticated = mSp
                        .getBoolean(PreferencesActivity.KEY_WAS_AUTHENTICATED, false);
                mCredentialsVerified = CredentialsVerified.load(mSp);

                mOAuth = mSp.getBoolean(PreferencesActivity.KEY_OAUTH, false);
                mPassword = mSp.getString(PreferencesActivity.KEY_TWITTER_PASSWORD, "");
            }
            if (copyGlobal || !mWasAuthenticated) {
                copyGlobal();
            }
        }
        updateDefaultSharedPreferences();
    }

    /**
     * Copy global (DefaultShared) preferences to this User's properties
     */
    private void copyGlobal() {
        SharedPreferences dsp = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean oauth = dsp.getBoolean(PreferencesActivity.KEY_OAUTH, false);
        if (mOAuth != oauth) {
            if (mWasAuthenticated) {
                clearAuthInformation();
            }
            setOAuth(oauth);
        }
        setPassword(dsp.getString(PreferencesActivity.KEY_TWITTER_PASSWORD, ""));
    }

    private static boolean isUsernameValid(String username) {
        boolean ok = false;
        if (username != null && (username.length() > 0)) {
            ok = Pattern.matches("[a-zA-Z_0-9\\.\\-\\(\\)]+", username);
            if (!ok && Log.isLoggable(AndTweetService.APPTAG, Log.INFO)) {
                Log.i(TAG, "The Username is not valid: \"" + username + "\"");
            }
        }
        return ok;
    }

    /**
     * Delete preferences file for this user
     * 
     * @param forNonAuthenticatedOnly
     * @return
     */
    public boolean deleteData(boolean forNonAuthenticatedOnly) {
        boolean isDeleted = false;
        if ((!forNonAuthenticatedOnly || !mWasAuthenticated) && mPrefsFileName.length() > 0) {
            // Old preferences file may be deleted, if it exists...
            isDeleted = SharedPreferencesUtil.delete(mContext, mPrefsFileName);
            if (isDeleted && Log.isLoggable(AndTweetService.APPTAG, Log.INFO)) {
                Log.i(TAG, "Data of the \"" + getUsername() + "\" User was deleted");
            }
        }
        return isDeleted;
    }

    /**
     * @param context
     * @return instance of Connection subtype for the User
     */
    public Connection getConnection() {
        if (mConnection == null) {
            mConnection = Connection.getConnection(mSp, mOAuth);
        }
        return mConnection;
    }

    /**
     * Clear Authentication information
     * 
     * @param context
     */
    public void clearAuthInformation() {
        setCredentialsVerified(CredentialsVerified.NEVER);
        setPassword("");
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
    private void setOAuth(boolean oauth) {
        if (mOAuth != oauth) {
            mConnection = null;
            mOAuth = oauth;
            mSp.edit().putBoolean(PreferencesActivity.KEY_OAUTH, oauth).commit();
        }
    }

    /**
     * @param oAuth to set
     */
    private void setPassword(String password) {
        if (password == null) {
            password = "";
        }
        if (password.compareTo(mPassword) != 0) {
            setCredentialsVerified(CredentialsVerified.NEVER);
            mConnection = null;
            mPassword = password;
            mSp.edit().putString(PreferencesActivity.KEY_TWITTER_PASSWORD, mPassword).commit();
            // Propagate the changes to the global properties
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putString(
                    PreferencesActivity.KEY_TWITTER_PASSWORD, mPassword).commit();
        }
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
     */
    public boolean verifyCredentials(boolean reVerify) throws ConnectionException,
            ConnectionUnavailableException, ConnectionAuthenticationException,
            SocketTimeoutException {
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
                if (ok) {
                    if (jso.optInt("id") < 1) {
                        ok = false;
                    }
                }
                if (ok) {
                    newName = Connection.getScreenName(jso);
                    if ((newName == null) || (newName.length() == 0)) {
                        ok = false;
                    }
                }
                if (ok) {
                    if (getUsername().length() == 0) {
                        setUsername(newName, false);
                    } else {
                        if (getUsername().compareTo(newName) != 0) {
                            // Credentials belong to other User ??
                            ok = false;
                        }
                    }
                }
                if (ok) {
                    setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                } else {
                    clearAuthInformation();
                    setCredentialsVerified(CredentialsVerified.FAILED);
                }
                updateDefaultSharedPreferences();
            }
        }
        return ok;
    }

    /**
     * Update global (default) SharedPreferences
     */
    public void updateDefaultSharedPreferences() {
        // Update global SharedPreferences
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(PreferencesActivity.KEY_TWITTER_USERNAME, getUsername());
        ed.putString(PreferencesActivity.KEY_TWITTER_PASSWORD, mPassword);
        ed.putBoolean(PreferencesActivity.KEY_OAUTH, isOAuth());
        getCredentialsVerified().put(ed);
        if (getCredentialsVerified() != CredentialsVerified.SUCCEEDED) {
            ed.putBoolean(PreferencesActivity.KEY_AUTOMATIC_UPDATES, false);
        }
        ed.commit();
    }
}
