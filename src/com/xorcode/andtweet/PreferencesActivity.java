/* 
 * Copyright (C) 2008 Torgny Bjers
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

import java.net.SocketTimeoutException;
import java.text.MessageFormat;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.RingtonePreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.widget.Toast;

import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;

/**
 * @author torgny.bjers
 * 
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener, OnPreferenceChangeListener {

	private static final String TAG = "AndTweetPreferences";

	private static final int DIALOG_AUTHENTICATION_FAILED = 1;
	private static final int DIALOG_CHECKING_CREDENTIALS = 2;
	private static final int DIALOG_SERVICE_UNAVAILABLE = 3;
	private static final int DIALOG_CONNECTION_TIMEOUT = 4;

	public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

	public static final String KEY_TWITTER_USERNAME = "twitter_username";
	public static final String KEY_TWITTER_PASSWORD = "twitter_password";
	public static final String KEY_HISTORY_SIZE = "history_size";
	public static final String KEY_HISTORY_TIME = "history_time";
	public static final String KEY_FETCH_FREQUENCY = "fetch_frequency";
	public static final String KEY_AUTOMATIC_UPDATES = "automatic_updates";
	public static final String KEY_RINGTONE_PREFERENCE = "notification_ringtone";
	//public static final String KEY_EXTERNAL_STORAGE = "storage_use_external";

	public static final int MSG_ACCOUNT_VALID = 1;
	public static final int MSG_ACCOUNT_INVALID = 2;
	public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 3;
	public static final int MSG_CONNECTION_EXCEPTION = 4;
	public static final int MSG_SOCKET_TIMEOUT_EXCEPTION = 5;

	private CheckBoxPreference mAutomaticUpdates;
	//private CheckBoxPreference mUseExternalStorage;
	private ListPreference mHistorySizePreference;
	private ListPreference mHistoryTimePreference;
	private ListPreference mFetchFrequencyPreference;
	private EditTextPreference mEditTextUsername;
	private EditTextPreference mEditTextPassword;
	private RingtonePreference mNotificationRingtone;

	private ProgressDialog mProgressDialog;

	protected boolean mAuthentiated = false;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		mHistorySizePreference = (ListPreference) getPreferenceScreen().findPreference(KEY_HISTORY_SIZE);
		mHistoryTimePreference = (ListPreference) getPreferenceScreen().findPreference(KEY_HISTORY_TIME);
		mFetchFrequencyPreference = (ListPreference) getPreferenceScreen().findPreference(KEY_FETCH_FREQUENCY);
		mAutomaticUpdates = (CheckBoxPreference) getPreferenceScreen().findPreference(KEY_AUTOMATIC_UPDATES);
		mNotificationRingtone = (RingtonePreference) getPreferenceScreen().findPreference(KEY_RINGTONE_PREFERENCE);
		mEditTextUsername = (EditTextPreference) getPreferenceScreen().findPreference(KEY_TWITTER_USERNAME);
		mEditTextPassword = (EditTextPreference) getPreferenceScreen().findPreference(KEY_TWITTER_PASSWORD);
		if (mEditTextUsername.getText() == null || mEditTextPassword.getText() == null || mEditTextUsername.getText().length() == 0 || mEditTextPassword.getText().length() == 0) {
			mAutomaticUpdates.setEnabled(false);
		}
		mNotificationRingtone.setOnPreferenceChangeListener(this);
		/*
		mUseExternalStorage = (CheckBoxPreference) getPreferenceScreen().findPreference(KEY_EXTERNAL_STORAGE);
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			mUseExternalStorage.setEnabled(false);
			mUseExternalStorage.setChecked(false);
		}
		*/
		updateFrequency();
		updateHistorySize();
		updateHistoryTime();
		updateRingtone(getPreferenceScreen().getSharedPreferences().getString(KEY_RINGTONE_PREFERENCE, null));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

    @Override
    protected void onPause() {
        super.onPause();

        //TODO: Is this necessary? AndTweetServiceManager.startAndTweetService(this);
        
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

	protected void updateHistorySize() {
		String[] k = getResources().getStringArray(R.array.history_size_keys);
		String[] d = getResources().getStringArray(R.array.history_size_display);
		String displayHistorySize = d[0];
		String historySize = mHistorySizePreference.getValue();
		for (int i = 0; i < k.length; i++) {
			if (historySize.equals(k[i])) {
				displayHistorySize = d[i];
				break;
			}
		}
		MessageFormat sf = new MessageFormat(getText(R.string.summary_preference_history_size).toString());
		mHistorySizePreference.setSummary(sf.format(new Object[] { displayHistorySize }));
	}

	protected void updateHistoryTime() {
		String[] k = getResources().getStringArray(R.array.history_time_keys);
		String[] d = getResources().getStringArray(R.array.history_time_display);
		String displayHistoryTime = d[0];
		String historyTime = mHistoryTimePreference.getValue();
		for (int i = 0; i < k.length; i++) {
			if (historyTime.equals(k[i])) {
				displayHistoryTime = d[i];
				break;
			}
		}
		MessageFormat sf = new MessageFormat(getText(R.string.summary_preference_history_time).toString());
		mHistoryTimePreference.setSummary(sf.format(new Object[] { displayHistoryTime }));
	}

	protected void updateFrequency() {
		String[] k = getResources().getStringArray(R.array.fetch_frequency_keys);
		String[] d = getResources().getStringArray(R.array.fetch_frequency_display);
		String displayFrequency = d[0];
		String frequency = mFetchFrequencyPreference.getValue();
		for (int i = 0; i < k.length; i++) {
			if (frequency.equals(k[i])) {
				displayFrequency = d[i];
				break;
			}
		}
		MessageFormat sf = new MessageFormat(getText(R.string.summary_preference_frequency).toString());
		mFetchFrequencyPreference.setSummary(sf.format(new Object[] { displayFrequency }));
	}

	protected void updateRingtone(Object newValue) {
		String ringtone = (String) newValue;
		Uri uri;
		Ringtone rt;
		if (ringtone == null) {
			uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
		} else if ("".equals(ringtone)) {
			mNotificationRingtone.setSummary(R.string.summary_preference_no_ringtone);
		} else {
			uri = Uri.parse(ringtone);
			rt = RingtoneManager.getRingtone(this, uri);
			mNotificationRingtone.setSummary(rt.getTitle(this));
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(KEY_FETCH_FREQUENCY)) {
			updateFrequency();
		}
		if (key.equals(KEY_TWITTER_USERNAME) || key.equals(KEY_TWITTER_PASSWORD)) {
			if (mEditTextUsername.getText() != null && mEditTextUsername.getText().length() > 0 && mEditTextPassword.getText() != null && mEditTextPassword.getText().length() > 0) {
				showDialog(DIALOG_CHECKING_CREDENTIALS);
				Thread thread = new Thread(mCheckCredentials);
				thread.start();
			}
		}
	};

	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference.getKey().equals(KEY_RINGTONE_PREFERENCE)) {
			updateRingtone(newValue);
			return true;
		}
		return false;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_AUTHENTICATION_FAILED:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_authentication_failed)
				.setMessage(R.string.dialog_summary_authentication_failed)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
					}
				}).create();

		case DIALOG_SERVICE_UNAVAILABLE:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_service_unavailable)
				.setMessage(R.string.dialog_summary_service_unavailable)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
					}
				}).create();

		case DIALOG_CHECKING_CREDENTIALS:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setTitle(R.string.dialog_title_checking_credentials);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_checking_credentials));
			return mProgressDialog;

		case DIALOG_CONNECTION_TIMEOUT:
			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_title_connection_timeout)
				.setMessage(R.string.dialog_summary_connection_timeout)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface Dialog, int whichButton) {
					}
				}).create();

		default:
			return super.onCreateDialog(id);
		}
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_ACCOUNT_VALID:
				mAuthentiated = true;
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				mAutomaticUpdates.setEnabled(true);
				Toast.makeText(PreferencesActivity.this, R.string.authentication_successful, Toast.LENGTH_SHORT).show();
				break;
			case MSG_ACCOUNT_INVALID:
				mAuthentiated = false;
				mAutomaticUpdates.setEnabled(false);
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				showDialog(DIALOG_AUTHENTICATION_FAILED);
				break;
			case MSG_SERVICE_UNAVAILABLE_ERROR:
				mAuthentiated = false;
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				showDialog(DIALOG_SERVICE_UNAVAILABLE);
				break;
			case MSG_CONNECTION_EXCEPTION:
				mAuthentiated = false;
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				int mId = 0;
				try {
					mId = Integer.parseInt((String) msg.obj);
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
				Toast.makeText(PreferencesActivity.this, mId, Toast.LENGTH_LONG).show();
				break;
			case MSG_SOCKET_TIMEOUT_EXCEPTION:
				mAuthentiated = false;
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				showDialog(DIALOG_CONNECTION_TIMEOUT);
				break;
			}
		}
	};

	private Runnable mCheckCredentials = new Runnable() {
		public void run() {
			Connection c = new Connection(mEditTextUsername.getText(), mEditTextPassword.getText());
			try {
				JSONObject jo = c.verifyCredentials();
				if (jo.optInt("id") > 0) {
					mHandler.sendMessage(mHandler.obtainMessage(MSG_ACCOUNT_VALID, 1, 0));
					return;
				}
			} catch (JSONException e) {
				Log.e(TAG, e.toString());
			} catch (ConnectionException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_CONNECTION_EXCEPTION, 1, 0, e.toString()));
				return;
			} catch (ConnectionAuthenticationException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_ACCOUNT_INVALID, 1, 0));
				return;
			} catch (ConnectionUnavailableException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SERVICE_UNAVAILABLE_ERROR, 1, 0));
				return;
			} catch (SocketTimeoutException e) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SOCKET_TIMEOUT_EXCEPTION, 1, 0));
				return;
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_ACCOUNT_INVALID, 1, 0));
		}
	};
}
