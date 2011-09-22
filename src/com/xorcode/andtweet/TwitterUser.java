/**
 * Copyright (C) 2010-2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.regex.Pattern;
import java.util.Vector;

import com.xorcode.andtweet.data.MyPreferences;
import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionCredentialsOfOtherUserException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionOAuth;
import com.xorcode.andtweet.net.ConnectionUnavailableException;
import com.xorcode.andtweet.util.MyLog;
import com.xorcode.andtweet.util.SharedPreferencesUtil;

import org.json.JSONObject;

/**
 * The object holds Twitter User's specific information including connection
 * TODO: Implement different data (tweets and their counters...) for different
 * Users.
 * 
 * @author Yuri Volkov
 */
public class TwitterUser {
    private static final String TAG = TwitterUser.class.getSimpleName();

    /**
     * Prefix of the user's Preferences file
     */
    public static final String FILE_PREFIX = "user_";

    /**
     * This is same name that is used in Twitter login
     */
    private String mUsername = "";

    /**
     * Was this user _ever_ authenticated?
     */
    private boolean mWasAuthenticated = false;

    /**
     * Was this user authenticated last time credentials were verified?
     * CredentialsVerified.NEVER - after changes of password/OAuth...
     */
    private CredentialsVerified mCredentialsVerified = CredentialsVerified.NEVER;

    private String mPrefsFileName = "";

    /**
     * Is this user authenticated with OAuth?
     */
    private boolean mOAuth = true;

    private Connection mConnection = null;

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

    public boolean getCredentialsPresent() {
        return getConnection().getCredentialsPresent(getSharedPreferences());
    }    
    
    public CredentialsVerified getCredentialsVerified() {
        return mCredentialsVerified;
    }

    public void setCredentialsVerified(CredentialsVerified cv) {
        mCredentialsVerified = cv;
        mCredentialsVerified.save(getSharedPreferences());
    }

    public void saveAuthInformation(String token, String secret) {
        if(isOAuth()) {
            ConnectionOAuth conn = ((ConnectionOAuth) getConnection());
            conn.saveAuthInformation(getSharedPreferences(), token, secret);
        } else {
            Log.e(TAG, "saveAuthInformation is for OAuth only!");
        }
    }
    
    /**
     * Forget everything in order to reread from the sources if it will be needed
     */
    public static void forget() {
        mTu = null;
    }
    
    /**
     * Get current user instance
     * 
     * @param Context
     * @return TwitterUser
     */
    public static TwitterUser getTwitterUser() {
        return getTwitterUser(null, false);
    }

    /**
     * Get user instance based on supplied twitter_username. Globally stored User
     * preferences are being set for the user, 
     * including oauth, password. 
     * New User is being created if user with such twitter_username
     * didn't exist.
     * 
     * @param Context
     * @return TwitterUser
     */
    public static TwitterUser getAddEditTwitterUser(String username) {
        return getTwitterUser(username, true);
    }

    /**
     * Get (stored) user instance by explicitly provided username. This user
     * becomes current user (Global SharedPreferences are being updated).
     * 
     * @param Context
     * @param username in Twitter
     * @return TwitterUser
     */
    public static TwitterUser getTwitterUser(String username) {
        return getTwitterUser(username, false);
    }

    /** 
     * Array of TwitterUser objects
     */
    private static Vector<TwitterUser> mTu = null;
    
    /**
     * Get list of all Users, including temporary (never authenticated) user
     * for the purpose of using these "accounts" elsewhere. Value of
     * {@link #getCredentialsVerified()} is the main differentiator.
     * 
     * @param context
     * @return Array of users
     */
    public static TwitterUser[] list() {
        if (mTu == null) {
            Log.e(TAG, "Was not initialized");
            return null;
        } else {
            return mTu.toArray(new TwitterUser[mTu.size()]);
        }
    }

    /**
     * Initialize internal static memory 
     * Initialize User's list if it wasn't initialized yet.
     * 
     * @param context
     */
    public static void initialize() {
        if (mTu == null) {
            mTu = new Vector<TwitterUser>();

            // Currently we don't hold user's list anywhere
            // So let's search user's files
            java.io.File prefsdir = new File(SharedPreferencesUtil.prefsDirectory(MyPreferences.getContext()));
            java.io.File files[] = prefsdir.listFiles();
            if (files != null) {
                for (int ind = 0; ind < files.length; ind++) {
                    if (files[ind].getName().startsWith(FILE_PREFIX)) {
                        String username = files[ind].getName().substring(FILE_PREFIX.length());
                        int indExtension = username.indexOf(".");
                        if (indExtension >= 0) {
                            username = username.substring(0, indExtension);
                        }
                        TwitterUser tu = new TwitterUser(username);
                        mTu.add(tu);
                    }
                }
            }
            MyLog.v(TAG, "User's list initialized, " + mTu.size() + " users");
        }
        else {
            MyLog.v(TAG, "Already initialized, " + mTu.size() + " users");
        }
    }

    /**
     * @param username
     * @return Name without path and extension
     */
    public static String prefsFileNameForUser(String username) {
        username = fixUsername(username);
        String fileName = FILE_PREFIX + username;
        return fileName;
    }

    /**
     * Factory of TwitterUser-s
     * 
     * @param Context
     * @param username in Twitter
     * @param copyGlobal globally stored User preferences are used, including:
     *            Username, OAuth, password. New User will be created if didn't
     *            exist yet.
     * @return TwitterUser - existed or newly created
     */
    private static TwitterUser getTwitterUser(String username, boolean copyGlobal) {
        // Find TwitterUser object for this user
        boolean found = false;
        int ind = -1;
        int indTemp = -1;
        TwitterUser tu = null;

        username = fixUsername(username);
        if (copyGlobal || (username.length() == 0)) {
            SharedPreferences dsp = MyPreferences.getDefaultSharedPreferences();
            username = fixUsername(dsp.getString(MyPreferences.KEY_TWITTER_USERNAME, ""));
        }
        for (ind = 0; ind < mTu.size(); ind++) {
            if (mTu.elementAt(ind).getUsername().compareTo(username) == 0) {
                found = true;
                break;
            }
            if (!mTu.elementAt(ind).wasAuthenticated()) {
                indTemp = ind;
            }
        }
        if (!found && indTemp >= 0) {
            // Let's don't keep more than one Temporary (never authenticated)
            // users. So delete previous User who wasn't ever authenticated.
            String tempUser = mTu.elementAt(indTemp).getUsername();
            delete(tempUser);
        }
        if (found) {
            tu = mTu.elementAt(ind);
            // AndTweetService.v(TAG, "User '" + tu.getUsername() + "' was found");
        } else {
            tu = new TwitterUser(username);
            MyLog.v(TAG, "New user '" + tu.getUsername() + "' was created");
            mTu.add(tu);
        }
        if (copyGlobal) {
            tu.copyGlobal();
        }
        return tu;
    }

    /**
     * Delete everything about the user
     * 
     * @return Was the User deleted?
     */
    public static boolean delete(String username) {
        boolean isDeleted = false;

        username = fixUsername(username);
        if (mTu == null) {
            Log.e(TAG, "delete: Was not initialized.");
        } else {
            // Delete the User's object from the list
            int ind = -1;
            boolean found = false;
            for (ind = 0; ind < mTu.size(); ind++) {
                if (mTu.elementAt(ind).getUsername().compareTo(username) == 0) {
                    found = true;
                    break;
                }
            }
            if (found) {
                TwitterUser tu = mTu.get(ind);
                tu.deleteData();

                // And delete the object from the list
                mTu.removeElementAt(ind);

                isDeleted = true;
            }
        }
        return isDeleted;
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

    /**
     * @param context
     * @param username
     */
    private TwitterUser(String username) {
        username = fixUsername(username);
        // Try to find saved User data
        mPrefsFileName = prefsFileNameForUser(username);
        boolean isNewUser = !SharedPreferencesUtil.exists(MyPreferences.getContext(), mPrefsFileName);
        setUsername(username, isNewUser);
        if (!isNewUser) {
            // Load stored data for the User
            SharedPreferences sp = getSharedPreferences();
            mWasAuthenticated = sp.getBoolean(MyPreferences.KEY_WAS_AUTHENTICATED, false);
            mCredentialsVerified = CredentialsVerified.load(sp);
            mOAuth = sp.getBoolean(MyPreferences.KEY_OAUTH, true);
        }
    }

    /**
     * @return the mUsername
     */
    public String getUsername() {
        return mUsername;
    }

    /**
     * @return SharedPreferences of this User
     */
    public SharedPreferences getSharedPreferences() {
        SharedPreferences sp = null;
        if (mPrefsFileName.length() > 0) {
            try {
                sp = MyPreferences.getSharedPreferences(mPrefsFileName, MODE_PRIVATE);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Cound't get preferences '" + mPrefsFileName + "'");
                sp = null;
            }
        }
        return sp;
    }

    /**
     * set Username for the User who was first time authenticated
     * 
     * @param username - new Username to set.
     */
    private boolean setUsernameAuthenticated(String username) {
        username = fixUsername(username);
        String newPrefsFileName = prefsFileNameForUser(username);
        boolean ok = false;

        if (!mWasAuthenticated) {
            // Do we really need to change it?
            ok = (mPrefsFileName.compareTo(newPrefsFileName) == 0);
            if (!ok) {
                mConnection = null;
                ok = SharedPreferencesUtil.rename(MyPreferences.getContext(), mPrefsFileName, newPrefsFileName);

                if (ok) {
                    mPrefsFileName = newPrefsFileName;
                }
                if (ok) {
                    // Now we know the name of this User!
                    setUsername(username, true);
                }
            }
            if (ok) {
                mWasAuthenticated = true;
                getSharedPreferences().edit().putBoolean(MyPreferences.KEY_WAS_AUTHENTICATED, true).commit();
            }
        }
        return ok;
    }

    /**
     * Sets Username for this object only, doesn't change "Current user" of the Application
     * @param username
     * @param isNewUser true if we are creating new user
     */
    private void setUsername(String username, boolean isNewUser) {
        username = fixUsername(username);
        if (username.compareTo(mUsername) != 0) {
            mConnection = null;
            mUsername = username;
            if (isNewUser) {
                getSharedPreferences().edit().putString(MyPreferences.KEY_TWITTER_USERNAME, mUsername).commit();
                // TODO: global method:
                getSharedPreferences()
                .edit()
                .putLong(MyPreferences.KEY_PREFERENCES_CHANGE_TIME,
                        java.lang.System.currentTimeMillis()).commit();

            }
        }
    }

    /**
     * Is this object - temporal (for user who was never authenticated)
     * 
     * @return
     */
    private boolean wasAuthenticated() {
        return mWasAuthenticated;
    }

    /**
     * Copy global (DefaultShared) preferences to this User's properties
     */
    private void copyGlobal() {
        SharedPreferences dsp = MyPreferences.getDefaultSharedPreferences();

        // Retrieve new values before changes so they won't be overridden
        boolean oauth = dsp.getBoolean(MyPreferences.KEY_OAUTH, true);
        String password = dsp.getString(MyPreferences.KEY_TWITTER_PASSWORD, "");

        // Make changes
        setOAuth(oauth);
        setPassword(password);
    }

    private static boolean isUsernameValid(String username) {
        boolean ok = false;
        if (username != null && (username.length() > 0)) {
            ok = Pattern.matches("[a-zA-Z_0-9\\.\\-\\(\\)]+", username);
            if (!ok && MyLog.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "The Username is not valid: \"" + username + "\"");
            }
        }
        return ok;
    }

    /**
     * Delete all User's data
     * 
     * @param forNonAuthenticatedOnly
     * @return
     */
    private boolean deleteData() {
        boolean isDeleted = false;

        if (wasAuthenticated()) {
            // TODO: Delete databases for this User

        }
        if (mPrefsFileName.length() > 0) {
            // Old preferences file may be deleted, if it exists...
            isDeleted = SharedPreferencesUtil.delete(MyPreferences.getContext(), mPrefsFileName);
        }

        return isDeleted;
    }

    /**
     * @param context
     * @return instance of Connection subtype for the User
     */
    public Connection getConnection() {
        if (mConnection == null) {
            mConnection = Connection.getConnection(getSharedPreferences(), mOAuth);
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
        this.getConnection().clearAuthInformation(getSharedPreferences());
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
            setCredentialsVerified(CredentialsVerified.NEVER);
            // So the Connection object may be reinitialized
            mConnection = null;
            mOAuth = oauth;
            getSharedPreferences().edit().putBoolean(MyPreferences.KEY_OAUTH, oauth).commit();
            // Propagate the changes to the global properties
            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(
                    MyPreferences.KEY_OAUTH, mOAuth).commit();
        }
    }

    /**
     * Password was moved to the connection object because it is needed there
     * 
     * @param password
     */
    private void setPassword(String password) {
        if (password.compareTo(getConnection().getPassword()) != 0) {
            setCredentialsVerified(CredentialsVerified.NEVER);
            getConnection().setPassword(getSharedPreferences(), password);
            // Propagate the changes to the global properties
            MyPreferences.getDefaultSharedPreferences().edit().putString(
                    MyPreferences.KEY_TWITTER_PASSWORD, getConnection().getPassword())
                    .commit();
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
                    if (getUsername().length() > 0 && getUsername().compareTo(newName) != 0) {
                        // Credentials belong to other User ??
                        ok = false;
                        credentialsOfOtherUser = true;
                    }
                }
                if (ok) {
                    setCredentialsVerified(CredentialsVerified.SUCCEEDED);
                }
                if (ok && !mWasAuthenticated) {
                    // Now we know the name of this User!
                    ok = setUsernameAuthenticated(newName);
                    if (!ok) {
                        errorSettingUsername = true;
                    }
                }
                if (!ok) {
                    clearAuthInformation();
                    setCredentialsVerified(CredentialsVerified.FAILED);
                }

                if (credentialsOfOtherUser) {
                    Log.e(TAG, MyPreferences.getContext().getText(R.string.error_credentials_of_other_user) + ": "
                            + newName);
                    throw (new ConnectionCredentialsOfOtherUserException());
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
     * Set current User to 'this' object.
     * - update global (default) SharedPreferences
     */
    public synchronized void setCurrentUser() {
        // Update global SharedPreferences
        SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();
        String usernameOld = sp.getString(MyPreferences.KEY_TWITTER_USERNAME, "");
        String usernameNew = getUsername();
        SharedPreferences.Editor ed = sp.edit();
        if (usernameNew.compareTo(usernameOld) != 0) {
            MyLog.v(TAG, "Changing current user from '" + usernameOld + "' " 
                    + "to '" + usernameNew + "'");
            // This preference is being set by PreferenceActivity etc.
            //ed.putString(PreferencesActivity.KEY_TWITTER_USERNAME_NEW, getUsername());
            // This preference is being set by this code only
            ed.putString(MyPreferences.KEY_TWITTER_USERNAME, getUsername());
        }
        ed.putString(MyPreferences.KEY_TWITTER_PASSWORD, getConnection().getPassword());
        ed.putBoolean(MyPreferences.KEY_OAUTH, isOAuth());
        getCredentialsVerified().put(ed);
        if (getCredentialsVerified() != CredentialsVerified.SUCCEEDED) {
            // Don't turn off Automatic updates
            // ed.putBoolean(PreferencesActivity.KEY_AUTOMATIC_UPDATES, false);
        }
        ed.commit();
    }
}
