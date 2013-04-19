/* 
 * Copyright (C) 2010-2012 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2010 Brion N. Emde, "BLOA" example, http://github.com/brione/Brion-Learns-OAuth 
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

import java.net.SocketTimeoutException;

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import oauth.signpost.OAuth;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

import org.andstatus.app.MyPreferenceActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount.CredentialsVerified;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.ConnectionAuthenticationException;
import org.andstatus.app.net.ConnectionCredentialsOfOtherUserException;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionOAuth;
import org.andstatus.app.net.ConnectionUnavailableException;
import org.andstatus.app.net.MyOAuth;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Add new or edit existing account
 * 
 * @author yvolk
 */
public class AccountSettings extends PreferenceActivity implements
        OnSharedPreferenceChangeListener, OnPreferenceChangeListener {
    private static final String TAG = AccountSettings.class.getSimpleName();

    public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

    /** Intent extras for launch directly from system account manager
     * NOTE: This string must match the one in res/xml/account_preferences.xml
     */
    private static final String ACTION_ACCOUNT_MANAGER_ENTRY =
        "org.andstatus.account.setup.ACCOUNT_MANAGER_ENTRY";
    /** 
     * NOTE: This constant should eventually be defined in android.accounts.Constants
     */
    private static final String EXTRA_ACCOUNT_MANAGER_ACCOUNT = "account";
    /**
     * Explicitly defined {@link MyAccount#getAccountGuid()}
     */
    public static final String EXTRA_MYACCOUNT_GUID = "myaccount_guid";
    
    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     */
    public static final Uri CALLBACK_URI = Uri.parse("andstatus-oauth://andstatus.org");

    // Request codes for called activities
    protected static final int REQUEST_SELECT_ACCOUNT = RESULT_FIRST_USER;
    
    /**
     * This is single list of (in fact, enums...) of Message/Dialog IDs
     */
    public static final int MSG_NONE = 1;

    public static final int MSG_ACCOUNT_VALID = 2;

    public static final int MSG_ACCOUNT_INVALID = 3;

    public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 4;

    public static final int MSG_CONNECTION_EXCEPTION = 5;

    public static final int MSG_SOCKET_TIMEOUT_EXCEPTION = 6;

    public static final int MSG_CREDENTIALS_OF_OTHER_USER = 7;

    // End Of the list ----------------------------------------

    /**
     * We are going to finish/restart this Activity
     */
    protected boolean mIsFinishing = false;
    
    /** State of the Account setup process that we store between the activity execution steps
    *   It's not proper to persist Bundle, 
    *   see: <a href="http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98">http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98</a>.
    *   
    *   This class will be close to com.android.email.activity.setup.SetupData
    **/
    private class StateClass {
        static final String ACCOUNT_ACTION_KEY = "account_action";
        static final String ACCOUNT_AUTHENTICATOR_RESPONSE_KEY = "account_authenticator_response";
        static final String ACCOUNT_KEY = "account";
        static final String ACTION_COMPLETED_KEY = "action_completed";
        static final String ACTION_SUCCEEDED_KEY = "action_succeeded";
        
        private String accountAction = Intent.ACTION_DEFAULT;
        boolean actionCompleted = true;
        boolean actionSucceeded = true;
        AccountAuthenticatorResponse response = null;
        MyAccount myAccount = null;
        
        /**
         * the state was restored
         */
        boolean restored = false;
        
        /**
         * Restore state if it was stored earlier
         */
        StateClass() {
            restored = restore();
        }

        /**
         * Don't restore previously stored state 
         */
        StateClass(String action) {
          setAccountAction(action);   
        }

        private void save(Bundle bundle) {
            if (bundle != null) {
                bundle.putString(ACCOUNT_ACTION_KEY, getAccountAction());
                bundle.putBoolean(ACTION_COMPLETED_KEY, actionCompleted);
                bundle.putBoolean(ACTION_SUCCEEDED_KEY, actionSucceeded);
                bundle.putParcelable(ACCOUNT_KEY, myAccount);
                bundle.putParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY, response);
                
                MyLog.v(TAG, "State saved to Bundle");
            }
        }

        private boolean restore(Bundle bundle) {
            boolean restored = false;
            if (bundle != null) {
                if (bundle.containsKey(ACTION_COMPLETED_KEY)) {
                    setAccountAction(bundle.getString(ACCOUNT_ACTION_KEY));
                    actionCompleted = bundle.getBoolean(ACTION_COMPLETED_KEY, true);
                    actionSucceeded = bundle.getBoolean(ACTION_SUCCEEDED_KEY);
                    myAccount = bundle.getParcelable(ACCOUNT_KEY);
                    response = bundle.getParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY);
                    restored = true;
                }
            }
            this.restored = restored;
            return restored;
        }
        
        /**
         * Store the state of the not completed actions in the global static object
         * or forget old state of completed action
         */
        void save() {
            if (actionCompleted) {
                forget();
            } else {
                AccountSettings.stateStored = new Bundle();
                save(AccountSettings.stateStored);
            }
        }
        
        boolean restore() {
            return restore(AccountSettings.stateStored);
        }

        /**
         * Forget stored state
         */
        void forget() {
            response = null;
            AccountSettings.stateStored = null;
        }

        String getAccountAction() {
            return accountAction;
        }

        void setAccountAction(String accountAction) {
            if (TextUtils.isEmpty(accountAction)) {
                this.accountAction = Intent.ACTION_DEFAULT;
            } else {
                this.accountAction = accountAction;
            }
        }

        /* The code below to store state in the SharedPreferences
         * works but it not usable because ACCOUNT_AUTHENTICATOR_RESPONSE_KEY
         * cannot be marshalled
         */
        /*
        static final String prefsFileName = AccountSettings.class.getSimpleName();
        static final String prefsKey = StateClass.class.getSimpleName();
        
        void save(Context context) {
            String str = "";
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName, MODE_PRIVATE);
            if (sp != null) {
                Bundle bundle = new Bundle();
                save(bundle);
                
                // This object cannot be marshalled (it contains Binder...)
                bundle.remove(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY);
                
                Parcel parcel = Parcel.obtain();
                bundle.writeToParcel(parcel, 0);
                //p.writeBundle(bundle);
                byte[] b = parcel.marshall();
                parcel.recycle();
                str = Base64.encodeToString(b, Base64.NO_WRAP + Base64.NO_PADDING);
                Log.v(TAG, "String to   file='" + str + "', bytes=" + b.length);
                sp.edit().putString(prefsKey, str).commit();

                Log.v(TAG, "State saved to file");
                
                // Try to read back to bundle
                bundle = new Bundle();
                parcel = Parcel.obtain();
                parcel.unmarshall(b, 0, b.length);
                parcel.setDataPosition(0);
                bundle.readFromParcel(parcel);
                parcel.recycle();
                Log.v(TAG, "Bundle test restore Ok");
                
            }
        }
        
        boolean restore(Context context) {
            SharedPreferences sp = MyPreferences.getSharedPreferences(prefsFileName, MODE_PRIVATE);
            if (sp != null) {
                String str = sp.getString(prefsKey, "");
                if (!TextUtils.isEmpty(str)) {
                    byte[] b = Base64.decode(str, 0);
                    Log.v(TAG, "String from file='" + str + "', bytes=" + b.length);
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(b, 0, b.length);
                    parcel.setDataPosition(0);
                    Bundle bundle = new Bundle();
                    bundle.readFromParcel(parcel);
                    // response = parcel.readParcelable(null);
                    parcel.recycle();
                    restore(bundle);
                    // And forget saved state
                    sp.edit().putString(prefsKey, "").commit();
                }
            }
            return restored;
        }
        */
        
    }
    private StateClass state = null;

    /** Stored state of single object of this class
     * It's static so it generally stays intact between the Activity's instantiations 
     * */
    private static Bundle stateStored = null;
    
    private CheckBoxPreference mOAuth;

    private EditTextPreference mEditTextUsername;

    private EditTextPreference mEditTextPassword;
    
    private ListPreference mOriginName;

    private Preference mVerifyCredentials;

    private boolean onSharedPreferenceChanged_busy = false;
    
    /**
     * Use this flag to return from this activity to “Accounts & Sync settings” screen
     */
    private boolean overrideBackButton = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyPreferences.initialize(this, this);
        
        addPreferencesFromResource(R.xml.account_settings);
        
        mOriginName = (ListPreference) findPreference(MyAccount.KEY_ORIGIN_NAME);
        mOAuth = (CheckBoxPreference) findPreference(MyAccount.KEY_OAUTH);
        mEditTextUsername = (EditTextPreference) findPreference(MyAccount.KEY_USERNAME_NEW);
        mEditTextPassword = (EditTextPreference) findPreference(MyAccount.KEY_PASSWORD);
        mVerifyCredentials = (Preference) findPreference(MyPreferences.KEY_VERIFY_CREDENTIALS);

        setState(getIntent(), "onCreate");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setState(intent, "onNewIntent");
    }

    /**
     * Restore previous state and set the Activity mode depending on input (Intent).
     * We should decide if we use stored state or create new based on information in intent
     * @param intent
     * @param calledFrom
     * @param savedInstanceState
     */
    private void setState(Intent intent, String calledFrom) {
        // We are creating new state?
        boolean isNew = false;
        String message = "";
        
        if (state == null)  {
            state =  new StateClass();
            message += (state.restored ? "Old state restored; " : "No previous state; ");
        } else {
            message += "State existed and " + (state.restored ? "restored earlier; " : "was not restored earlier; ");
        }
        isNew = state.actionCompleted;
        
        StateClass stateNew = new StateClass(intent.getAction());

        Bundle extras = intent.getExtras();
        if (extras != null) {
            // For a usage example see also com.android.email.activity.setup.AccountSettings.onCreate(Bundle)

            // Unparcel Extras!
            stateNew.response = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
            if (stateNew.response != null) {
                // In order to force initializing state
                isNew = true;
            }
            android.accounts.Account ac = null;
            if (android.os.Build.VERSION.SDK_INT < 16 ) {  // before Jelly Bean
                // Starting with Jelly Bean (16) there is only one link for the the setting of all AndStatus accounts
                // So we must select account in our code
                ac = (android.accounts.Account) intent
                        .getParcelableExtra(EXTRA_ACCOUNT_MANAGER_ACCOUNT);
            }
            if (ac != null) {
                // We have persistent account in the intent
                stateNew.myAccount = MyAccount.getMyAccount(ac, true);
                isNew = true;
            } else {
                // Maybe we received MyAccount name as as parameter?!
                String accountGuid = extras.getString(EXTRA_MYACCOUNT_GUID);
                if (!TextUtils.isEmpty(accountGuid)) {
                    stateNew.myAccount = MyAccount.getMyAccount(accountGuid);
                    isNew = stateNew.myAccount.isPersistent();
                }
            }
        }

        if (isNew) {
            message += "State initialized; ";
            state = stateNew;
            if (state.myAccount == null && !state.getAccountAction().equals(Intent.ACTION_INSERT)) {
                if (state.getAccountAction().equals(ACTION_ACCOUNT_MANAGER_ENTRY) && android.os.Build.VERSION.SDK_INT < 16) {
                    // This case occurs if we're changing account settings from Settings -> Accounts
                    state.setAccountAction(Intent.ACTION_INSERT);
                } else {
                    message += "Select Account; ";
                    Intent i = new Intent(this, AccountSelector.class);
                    startActivityForResult(i, REQUEST_SELECT_ACCOUNT);
                }
            }
            
            if (state.myAccount == null) {
                if (!state.getAccountAction().equals(Intent.ACTION_INSERT)) {
                    state.myAccount = MyAccount.getCurrentMyAccount();
                }
                if (state.myAccount == null) {
                    state.setAccountAction(Intent.ACTION_INSERT);
                    state.myAccount = MyAccount.getMyAccount("");
                    // Check if there are changes to avoid "ripples"
                    // ...
                    // TODO check this: state.actionCompleted = false;
                } else {
                    state.setAccountAction(Intent.ACTION_VIEW);
                }
            } else {
                if (state.myAccount.isPersistent()) {
                    state.setAccountAction(Intent.ACTION_EDIT);
                } else {
                    state.setAccountAction(Intent.ACTION_INSERT);
                }
            }

            message += "action=" + state.getAccountAction() + "; ";

            showUserPreferences();
        }
        
        if (state.response != null) {
            message += "response; ";
        }
        MyLog.v(TAG, "setState from " + calledFrom +"; " + message + "intent=" + intent.toUri(0));
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    state.myAccount = MyAccount.getCurrentMyAccount();
                    if (state.myAccount == null) {
                        mIsFinishing = true;
                    }
                } else {
                    mIsFinishing = true;
                }
                if (!mIsFinishing) {
                    MyLog.v(TAG, "Switching to the selected account");
                    state.setAccountAction(Intent.ACTION_EDIT);
                    showUserPreferences();
                } else {
                    MyLog.v(TAG, "No account supplied, finishing");
                    finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    /**
     * Show values of all preferences in the "summaries".
     * @see <a href="http://stackoverflow.com/questions/531427/how-do-i-display-the-current-value-of-an-android-preference-in-the-preference-sum"> 
       How do I display the current value of an Android Preference 
       in the Preference summary?</a>
       
     * Some "preferences" may be changed in MyAccount object
     */
    private void showUserPreferences() {
        MyAccount ma = state.myAccount;
        
        mOriginName.setValue(ma.getOriginName());
        SharedPreferencesUtil.showListPreference(this, MyAccount.KEY_ORIGIN_NAME, R.array.origin_system_entries, R.array.origin_system_entries, R.string.summary_preference_origin_system);
        mOriginName.setEnabled(!ma.isPersistent());
        
        if (mEditTextUsername.getText() == null
                || ma.getUsername().compareTo(mEditTextUsername.getText()) != 0) {
            mEditTextUsername.setText(ma.getUsername());
        }
        StringBuilder summary = new StringBuilder(this.getText(R.string.summary_preference_username));
        if (ma.getUsername().length() > 0) {
            summary.append(": " + ma.getUsername());
        } else {
            summary.append(": (" + this.getText(R.string.not_set) + ")");
        }
        mEditTextUsername.setSummary(summary);
        mEditTextUsername.setEnabled(ma.canSetUsername());

        if (ma.isOAuth() != mOAuth.isChecked()) {
            mOAuth.setChecked(ma.isOAuth());
        }
        // In fact, we should hide it if not enabled, but I couldn't find an easy way for this...
        mOAuth.setEnabled(ma.canChangeOAuth());

        if (mEditTextPassword.getText() == null
                || ma.getPassword().compareTo(mEditTextPassword.getText()) != 0) {
            mEditTextPassword.setText(ma.getPassword());
        }
        summary = new StringBuilder(this.getText(R.string.summary_preference_password));
        if (TextUtils.isEmpty(ma.getPassword())) {
            summary.append(": (" + this.getText(R.string.not_set) + ")");
        }
        mEditTextPassword.setSummary(summary);
        mEditTextPassword.setEnabled(ma.getConnection().isPasswordNeeded());

        int titleResId;
        switch (ma.getCredentialsVerified()) {
            case SUCCEEDED:
                titleResId = R.string.title_preference_verify_credentials;
                summary = new StringBuilder(
                        this.getText(R.string.summary_preference_verify_credentials));
                break;
            default:
                if (ma.isPersistent()) {
                    titleResId = R.string.title_preference_verify_credentials_failed;
                    summary = new StringBuilder(
                            this.getText(R.string.summary_preference_verify_credentials_failed));
                } else {
                    titleResId = R.string.title_preference_add_account;
                    if (ma.isOAuth()) {
                        summary = new StringBuilder(
                                this.getText(R.string.summary_preference_add_account_oauth));
                    } else {
                        summary = new StringBuilder(
                                this.getText(R.string.summary_preference_add_account_basic));
                    }
                }
                break;
        }
        mVerifyCredentials.setTitle(titleResId);
        mVerifyCredentials.setSummary(summary);
        mVerifyCredentials.setEnabled(ma.isOAuth() || ma.getCredentialsPresent());
    }

    @Override
    protected void onResume() {
        super.onResume();

        showUserPreferences();
        MyPreferences.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        
        Uri uri = getIntent().getData();
        if (uri != null) {
            if (MyLog.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "uri=" + uri.toString());
            }
            if (CALLBACK_URI.getScheme().equals(uri.getScheme())) {
                // To prevent repeating of this task
                getIntent().setData(null);
                // This activity was started by Twitter ("Service Provider")
                // so start second step of OAuth Authentication process
                new OAuthAcquireAccessTokenTask().execute(uri);
                // and return back to default screen
                overrideBackButton = true;
            }
        }
    }

    /**
     * Verify credentials
     * 
     * @param reVerify true - Verify only if we didn't do this yet
     */
    private void verifyCredentials(boolean reVerify) {
        MyAccount ma = state.myAccount;
        if (reVerify || ma.getCredentialsVerified() == CredentialsVerified.NEVER) {
            if (ma.getCredentialsPresent()) {
                // Credentials are present, so we may verify them
                // This is needed even for OAuth - to know Twitter Username
                new VerifyCredentialsTask().execute();
            } else {
                if (ma.isOAuth() && reVerify) {
                    // Credentials are not present,
                    // so start asynchronous OAuth Authentication process 
                    new OAuthAcquireRequestTokenTask().execute();
                    // and return back to default screen
                    overrideBackButton = true;
                }
            }

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        state.save();
        MyPreferences.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
    }

    /**
     * Only preferences with android:persistent="true" trigger this event! 
     */
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (AccountSettings.this.mSomethingIsBeingProcessed) {
            return;
        }
        if (onSharedPreferenceChanged_busy || !MyPreferences.isInitialized()) {
            return;
        }
        onSharedPreferenceChanged_busy = true;

        try {
            String value = "(not set)";
            if (sharedPreferences.contains(key)) {
                try {
                    value = sharedPreferences.getString(key, "");
                } catch (ClassCastException e) {
                    try {
                        value = Boolean.toString(sharedPreferences.getBoolean(key, false));
                    } catch (ClassCastException e2) {
                        value = "??";
                    }
                }
            }
            MyLog.d(TAG, "onSharedPreferenceChanged: " + key + "='" + value + "'");

            // Here and below:
            // Check if there are changes to avoid "ripples": don't set new value if no changes
            
            if (key.equals(MyAccount.KEY_ORIGIN_NAME)) {
                if (state.myAccount.getOriginName().compareToIgnoreCase(mOriginName.getValue()) != 0) {
                    // If we change the System, we should recreate the Account
                    state.myAccount = MyAccount.getMyAccount(mOriginName.getValue() + "/" + state.myAccount.getUsername());
                    showUserPreferences();
                }
            }
            if (key.equals(MyAccount.KEY_OAUTH)) {
                if (state.myAccount.isOAuth() != mOAuth.isChecked()) {
                    state.myAccount.setOAuth(mOAuth.isChecked());
                    showUserPreferences();
                }
            }
            if (key.equals(MyAccount.KEY_USERNAME_NEW)) {
                String usernameNew = mEditTextUsername.getText();
                if (usernameNew.compareTo(state.myAccount.getUsername()) != 0) {
                    boolean oauth = state.myAccount.isOAuth();
                    // TODO: maybe this is not enough...
                    state.myAccount = MyAccount.getMyAccount(state.myAccount.getOriginName() + "/" + usernameNew);
                    state.myAccount.setOAuth(oauth);
                    showUserPreferences();
                }
            }
            if (key.equals(MyAccount.KEY_PASSWORD)) {
                if (state.myAccount.getPassword().compareTo(mEditTextPassword.getText()) != 0) {
                    state.myAccount.setPassword(mEditTextPassword.getText());
                    showUserPreferences();
                }
            }
        } finally {
            onSharedPreferenceChanged_busy = false;
        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        int titleId = 0;
        int summaryId = 0;
        Dialog dlg = null;

        switch (id) {
            case MSG_ACCOUNT_INVALID:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_authentication_failed;
                    summaryId = R.string.dialog_summary_authentication_failed;
                }
            case MSG_SERVICE_UNAVAILABLE_ERROR:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_service_unavailable;
                    summaryId = R.string.dialog_summary_service_unavailable;
                }
            case MSG_SOCKET_TIMEOUT_EXCEPTION:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_connection_timeout;
                    summaryId = R.string.dialog_summary_connection_timeout;
                }
            case MSG_CREDENTIALS_OF_OTHER_USER:
                if (titleId == 0) {
                    titleId = R.string.dialog_title_authentication_failed;
                    summaryId = R.string.error_credentials_of_other_user;
                }
                dlg = new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(titleId)
                        .setMessage(summaryId)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface Dialog,
                                            int whichButton) {
                                    }
                                }).create();

            default:
                dlg = super.onCreateDialog(id);
        }
        return dlg;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        // TODO:
    }

    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    private boolean mSomethingIsBeingProcessed = false;

    /*
     * (non-Javadoc)
     * @seeandroid.preference.PreferenceActivity#onPreferenceTreeClick(android.
     * preference.PreferenceScreen, android.preference.Preference)
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        MyLog.d(TAG, "Preference clicked:" + preference.toString());
        if (preference.getKey().compareTo(MyPreferences.KEY_VERIFY_CREDENTIALS) == 0) {
            verifyCredentials(true);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    };
   
    /**
     * Assuming we already have credentials to verify, verify them
     * @author yvolk
     *
     */
    private class VerifyCredentialsTask extends AsyncTask<Void, Void, JSONObject> {
        private ProgressDialog dlg;
        private boolean skip = false;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(AccountSettings.this,
                    getText(R.string.dialog_title_checking_credentials),
                    getText(R.string.dialog_summary_checking_credentials), true, // indeterminate
                    // duration
                    false); // not cancel-able

            synchronized (AccountSettings.this) {
                if (AccountSettings.this.mSomethingIsBeingProcessed) {
                    skip = true;
                } else {
                    AccountSettings.this.mSomethingIsBeingProcessed = true;
                }
            }
        }

        @Override
        protected JSONObject doInBackground(Void... arg0) {
            JSONObject jso = null;

            int what = MSG_NONE;
            String message = "";
            
            if (!skip) {
                what = MSG_ACCOUNT_INVALID;
                try {
                    if (state.myAccount.verifyCredentials(true)) {
                        what = MSG_ACCOUNT_VALID;
                    }
                } catch (ConnectionException e) {
                    what = MSG_CONNECTION_EXCEPTION;
                    message = e.toString();
                } catch (ConnectionAuthenticationException e) {
                    what = MSG_ACCOUNT_INVALID;
                } catch (ConnectionCredentialsOfOtherUserException e) {
                    what = MSG_CREDENTIALS_OF_OTHER_USER;
                } catch (ConnectionUnavailableException e) {
                    what = MSG_SERVICE_UNAVAILABLE_ERROR;
                } catch (SocketTimeoutException e) {
                    what = MSG_SOCKET_TIMEOUT_EXCEPTION;
                }
            }

            try {
                jso = new JSONObject();
                jso.put("what", what);
                jso.put("message", message);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        /**
         * Credentials were verified just now!
         * This is in the UI thread, so we can mess with the UI
         */
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            boolean succeeded = false;
            if (jso != null) {
                try {
                    int what = jso.getInt("what");
                    String message = jso.getString("message");

                    switch (what) {
                        case MSG_ACCOUNT_VALID:
                            Toast.makeText(AccountSettings.this, R.string.authentication_successful,
                                    Toast.LENGTH_SHORT).show();
                            succeeded = true;
                            break;
                        case MSG_ACCOUNT_INVALID:
                        case MSG_SERVICE_UNAVAILABLE_ERROR:
                        case MSG_SOCKET_TIMEOUT_EXCEPTION:
                        case MSG_CREDENTIALS_OF_OTHER_USER:
                            showDialog(what);
                            break;
                        case MSG_CONNECTION_EXCEPTION:
                            int mId = 0;
                            try {
                                mId = Integer.parseInt(message);
                            } catch (Exception e) {
                            }
                            switch (mId) {
                                case 404:
                                    mId = R.string.error_twitter_404;
                                    break;
                                default:
                                    mId = R.string.error_connection_error;
                                    break;
                            }
                            Toast.makeText(AccountSettings.this, mId, Toast.LENGTH_LONG).show();
                            break;

                    }
                    showUserPreferences();
                } catch (JSONException e) {
                    // Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (!skip) {
                StateClass state = AccountSettings.this.state;
                // Note: MyAccount was already saved inside MyAccount.verifyCredentials
                // Now we only have to deal with the state
               
                state.actionSucceeded = succeeded;
                if (succeeded) {
                    state.actionCompleted = true;
                    if (state.getAccountAction().compareTo(Intent.ACTION_INSERT) == 0) {
                        closeAndGoBack();
                    }
                }

                AccountSettings.this.mSomethingIsBeingProcessed = false;
            }
            showUserPreferences();
        }
    }
    
    /**
     * Task 1 of 2 required for OAuth Authentication.
     * See http://www.snipe.net/2009/07/writing-your-first-twitter-application-with-oauth/
     * for good OAuth Authentication flow explanation.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") Requests "Request Token" from Twitter ("Service provider"), 
     * 2. Waits that Request Token
     * 3. Consumer directs User to Service Provider: opens Twitter site in Internet Browser window
     *    in order to Obtain User Authorization.
     * 4. This task ends.
     * 
     * What will occur later:
     * 5. After User Authorized AndStatus in the Internet Browser,
     *    Twitter site will redirect User back to
     *    AndStatus and then the second OAuth task, , will start.
     *   
     * @author yvolk. This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireRequestTokenTask extends AsyncTask<Void, Void, JSONObject> {
        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(AccountSettings.this,
                    getText(R.string.dialog_title_acquiring_a_request_token),
                    getText(R.string.dialog_summary_acquiring_a_request_token), true, // indeterminate
                    // duration
                    false); // not cancel-able
        }

        @Override
        protected JSONObject doInBackground(Void... arg0) {
            JSONObject jso = null;

            boolean requestSucceeded = false;
            String message = "";
            String message2 = "";
            try {
                MyAccount ma = state.myAccount;
                MyOAuth oa = ma.getOAuth();

                // This is really important. If you were able to register your
                // real callback Uri with Twitter, and not some fake Uri
                // like I registered when I wrote this example, you need to send
                // null as the callback Uri in this function call. Then
                // Twitter will correctly process your callback redirection
                String authUrl = oa.getProvider().retrieveRequestToken(oa.getConsumer(),
                        CALLBACK_URI.toString());
                saveRequestInformation(ma, oa.getConsumer().getToken(), oa.getConsumer().getTokenSecret());

                // This is needed in order to complete the process after redirect
                // from the Browser to the same activity.
                state.actionCompleted = false;
                
                // Start Internet Browser
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
                // Trying to skip Browser activities on Back button press
                i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                AccountSettings.this.startActivity(i);

                requestSucceeded = true;
            } catch (OAuthMessageSignerException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthNotAuthorizedException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthExpectationFailedException e) {
                message = e.getMessage();
                e.printStackTrace();
            } catch (OAuthCommunicationException e) {
                message = e.getMessage();
                e.printStackTrace();
            }

            try {
                // mSp.edit().putBoolean(ConnectionOAuth.REQUEST_SUCCEEDED,
                // requestSucceeded).commit();
                if (!requestSucceeded) {
                    message2 = AccountSettings.this
                            .getString(R.string.dialog_title_authentication_failed);
                    if (message != null && message.length() > 0) {
                        message2 = message2 + ": " + message;
                    }
                    MyLog.d(TAG, message2);
                }

                // This also works sometimes, but message2 may have quotes...
                // String jss = "{\n\"succeeded\": \"" + requestSucceeded
                // + "\",\n\"message\": \"" + message2 + "\"}";
                // jso = new JSONObject(jss);

                jso = new JSONObject();
                jso.put("succeeded", requestSucceeded);
                jso.put("message", message2);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        // This is in the UI thread, so we can mess with the UI
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    if (succeeded) {
                        // This may be necessary in order to start properly 
                        // after redirection from Twitter
                        // Because of initializations in onCreate...
                        // TODO: ???
                        // AccountSettings.this.finish();
                    } else {
                        Toast.makeText(AccountSettings.this, message, Toast.LENGTH_LONG).show();

                        state.myAccount.setCredentialsVerified(CredentialsVerified.FAILED);
                        showUserPreferences();
                    }
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * Task 2 of 2 required for OAuth Authentication.
     *  
     * During this task:
     * 1. AndStatus ("Consumer") exchanges "Request Token", 
     *    obtained earlier from Twitter ("Service provider"),
     *    for "Access Token". 
     * 2. Stores the Access token for all future interactions with Twitter.
     * 
     * @author yvolk. This code is based on "BLOA" example,
     *         http://github.com/brione/Brion-Learns-OAuth yvolk: I had to move
     *         this code from OAuthActivity here in order to be able to show
     *         ProgressDialog and to get rid of any "Black blank screens"
     */
    private class OAuthAcquireAccessTokenTask extends AsyncTask<Uri, Void, JSONObject> {
        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(AccountSettings.this,
                    getText(R.string.dialog_title_acquiring_an_access_token),
                    getText(R.string.dialog_summary_acquiring_an_access_token), true, // indeterminate
                    // duration
                    false); // not cancelable
        }

        @Override
        protected JSONObject doInBackground(Uri... uris) {
            JSONObject jso = null;

            String message = "";
            
            boolean authenticated = false;
            MyAccount ma = state.myAccount;
            // We don't need to worry about any saved states: we can reconstruct
            // the state
            MyOAuth oa = ma.getOAuth();

            if (oa == null) {
                message = "Connection is not OAuth";
                Log.e(TAG, message);
            }
            else {
                Uri uri = uris[0];
                if (uri != null && CALLBACK_URI.getScheme().equals(uri.getScheme())) {
                    String token = ma.getDataString(ConnectionOAuth.REQUEST_TOKEN, null);
                    String secret = ma.getDataString(ConnectionOAuth.REQUEST_SECRET, null);

                    ma.clearAuthInformation();
                    try {
                        // Clear the request stuff, we've used it already
                        saveRequestInformation(ma, null, null);

                        if (!(token == null || secret == null)) {
                            oa.getConsumer().setTokenWithSecret(token, secret);
                        }
                        String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
                        String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

                        /*
                         * yvolk 2010-07-08: It appeared that this may be not true:
                         * Assert.assertEquals(otoken, mConsumer.getToken()); (e.g.
                         * if User denied access during OAuth...) hence this is not
                         * Assert :-)
                         */
                        if (otoken != null || oa.getConsumer().getToken() != null) {
                            // We send out and save the request token, but the
                            // secret is not the same as the verifier
                            // Apparently, the verifier is decoded to get the
                            // secret, which is then compared - crafty
                            // This is a sanity check which should never fail -
                            // hence the assertion
                            // Assert.assertEquals(otoken,
                            // mConsumer.getToken());

                            // This is the moment of truth - we could throw here
                            oa.getProvider().retrieveAccessToken(oa.getConsumer(), verifier);
                            // Now we can retrieve the goodies
                            token = oa.getConsumer().getToken();
                            secret = oa.getConsumer().getTokenSecret();
                            authenticated = true;
                        }
                    } catch (OAuthMessageSignerException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthNotAuthorizedException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthExpectationFailedException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } catch (OAuthCommunicationException e) {
                        message = e.getMessage();
                        e.printStackTrace();
                    } finally {
                        if (authenticated) {
                            ma.setAuthInformation(token, secret);
                        }
                    }
                }
            }

            try {
                jso = new JSONObject();
                jso.put("succeeded", authenticated);
                jso.put("message", message);
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return jso;
        }

        // This is in the UI thread, so we can mess with the UI
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    MyLog.d(TAG, this.getClass().getName() + " ended, "
                            + (succeeded ? "authenticated" : "authentication failed"));
                    
                    if (succeeded) {
                        // Credentials are present, so we may verify them
                        // This is needed even for OAuth - to know Twitter Username
                        new VerifyCredentialsTask().execute();

                    } else {
                        String message2 = AccountSettings.this
                        .getString(R.string.dialog_title_authentication_failed);
                        if (message != null && message.length() > 0) {
                            message2 = message2 + ": " + message;
                            Log.d(TAG, message);
                        }
                        Toast.makeText(AccountSettings.this, message2, Toast.LENGTH_LONG).show();

                        state.myAccount.setCredentialsVerified(CredentialsVerified.FAILED);
                        showUserPreferences();
                    }
                    
                    // Now we can return to the AccountSettings
                    // We need new Intent in order to forget that URI from OAuth Service Provider
                    //Intent intent = new Intent(AccountSettings.this, AccountSettings.class);
                    //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    //startActivity(intent);
                    
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
    public static void saveRequestInformation(MyAccount ma, String token,
            String secret) {
        // null means to clear the old values
        ma.setDataString(ConnectionOAuth.REQUEST_TOKEN, token);
        MyLog.d(TAG, TextUtils.isEmpty(token) ? "Clearing Request Token" : "Saving Request Token: " + token);
        ma.setDataString(ConnectionOAuth.REQUEST_SECRET, token);
        MyLog.d(TAG, TextUtils.isEmpty(token) ? "Clearing Request Secret" : "Saving Request Secret: " + secret);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            // Explicitly save MyAccount only on "Back key" 
            state.myAccount.save();
            closeAndGoBack();
        }
        if (mIsFinishing) {
            return true;    
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /** 
     * Mark the action completed, close this activity and go back to the proper screen.
     * Return result to the caller if necessary.
     * See also {@link com.android.email.activity.setup.AccountSetupBasics.finish}
     * 
     * @return
     */
    private boolean closeAndGoBack() {
        boolean doFinish = false;
        String message = "";
        state.actionCompleted = true;
        if (state.response != null) {
            // We should return result back to AccountManager
            if (state.actionSucceeded) {
                if (state.myAccount.isPersistent()) {
                    doFinish = true;
                    // Pass the new/edited account back to the account manager
                    Bundle result = new Bundle();
                    result.putString(AccountManager.KEY_ACCOUNT_NAME, state.myAccount.getAccountGuid());
                    result.putString(AccountManager.KEY_ACCOUNT_TYPE,
                            AuthenticatorService.ANDROID_ACCOUNT_TYPE);
                    state.response.onResult(result);
                    message += "response; account.name=" + state.myAccount.getAccount().name + "; ";
                }
            } else {
                state.response.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
        }
        // Forget old state
        state.forget();
        if (overrideBackButton) {
            doFinish = true;
        }
        if (doFinish) {
            MyLog.v(TAG, "finish: action=" + state.getAccountAction() + "; " + message);
            mIsFinishing = true;
            finish();
        }
        if (overrideBackButton) {
            MyLog.v(TAG, "Returning to our Preferences Activity");
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            Intent i = new Intent(this, MyPreferenceActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        return mIsFinishing;
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "onPreferenceChange: " + preference.toString() + " -> " + (newValue == null ? "null" : newValue.toString()));
        }
        return true;
    }
    
    /**
     * Start system activity which allow to manage list of accounts
     * See <a href="https://groups.google.com/forum/?fromgroups#!topic/android-developers/RfrIb5V_Bpo">per account settings in Jelly Beans</a>. 
     * For versions prior to Jelly Bean see <a href="http://stackoverflow.com/questions/3010103/android-how-to-create-intent-to-open-the-activity-that-displays-the-accounts">
     *  Android - How to Create Intent to open the activity that displays the “Accounts & Sync settings” screen</a>
     */
    public static void startManageAccountsActivity(android.content.Context context) {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT < 16 ) {  // before Jelly Bean
            intent = new Intent(android.provider.Settings.ACTION_SYNC_SETTINGS);
            // This gives some unstable results on v.4.0.x so I got rid of it:
            // intent.putExtra(android.provider.Settings.EXTRA_AUTHORITIES, new String[] {MyProvider.AUTHORITY});
        } else {
            intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            // TODO: Find out some more specific intent...
        }
        context.startActivity(intent);
    }
}
