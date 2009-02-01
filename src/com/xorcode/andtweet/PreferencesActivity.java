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

import java.text.MessageFormat;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionException;

/**
 * @author torgny.bjers
 * 
 */
public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = "AndTweetDatabase";

	private static final int DIALOG_AUTHENTICATION_FAILED = 1;
	private static final int DIALOG_CHECKING_CREDENTIALS = 2;

	public static final String INTENT_RESULT_KEY_AUTHENTICATION = "authentication";

	public static final String KEY_TWITTER_USERNAME = "twitter_username";
	public static final String KEY_TWITTER_PASSWORD = "twitter_password";
	public static final String KEY_FETCH_FREQUENCY = "fetch_frequency";
	public static final String KEY_AUTOMATIC_UPDATES = "automatic_updates";

	public static final int MSG_ACCOUNT_VALID = 1;
	public static final int MSG_ACCOUNT_INVALID = 2;

	private CheckBoxPreference mAutomaticUpdates;
	private ListPreference mFetchFrequencyPreference;
	private EditTextPreference mEditTextUsername;
	private EditTextPreference mEditTextPassword;

	private ProgressDialog mProgressDialog;

	protected boolean mAuthentiated = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		mFetchFrequencyPreference = (ListPreference) getPreferenceScreen().findPreference(KEY_FETCH_FREQUENCY);
		mAutomaticUpdates = (CheckBoxPreference) getPreferenceScreen().findPreference(KEY_AUTOMATIC_UPDATES);
		mEditTextUsername = (EditTextPreference) getPreferenceScreen().findPreference(KEY_TWITTER_USERNAME);
		mEditTextPassword = (EditTextPreference) getPreferenceScreen().findPreference(KEY_TWITTER_PASSWORD);
		if (mEditTextUsername.getText() == null || mEditTextPassword.getText() == null || mEditTextUsername.getText().length() == 0 || mEditTextPassword.getText().length() == 0) {
			mAutomaticUpdates.setEnabled(false);
			mAutomaticUpdates.setChecked(false);
		}
		updateFrequency();
	}

	@Override
	protected void onResume() {
		super.onResume();
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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

		case DIALOG_CHECKING_CREDENTIALS:
			mProgressDialog = new ProgressDialog(this);
			mProgressDialog.setTitle(R.string.dialog_title_checking_credentials);
			mProgressDialog.setMessage(getText(R.string.dialog_summary_checking_credentials));
			return mProgressDialog;

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
				Log.d(TAG, "account valid");
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				mAutomaticUpdates.setEnabled(true);
				Toast.makeText(PreferencesActivity.this, R.string.authentication_successful, Toast.LENGTH_SHORT).show();
				break;
			case MSG_ACCOUNT_INVALID:
				mAuthentiated = false;
				Log.d(TAG, "account invalid");
				mAutomaticUpdates.setChecked(false);
				mAutomaticUpdates.setEnabled(false);
				dismissDialog(DIALOG_CHECKING_CREDENTIALS);
				showDialog(DIALOG_AUTHENTICATION_FAILED);
				break;
			}
		}
	};

	private Runnable mCheckCredentials = new Runnable() {
		public void run() {
			Log.d(TAG, "Started thread");
			Connection c = new Connection(mEditTextUsername.getText(), mEditTextPassword.getText());
			try {
				JSONObject jo = c.verifyCredentials();
				Log.d(TAG, jo.optString("id"));
				Log.d(TAG, jo.optString("user"));
				if (jo.optString("error").equals("Not found")) {
					mHandler.sendMessage(mHandler.obtainMessage(MSG_ACCOUNT_VALID, 1, 0));
					return;
				}
			} catch (JSONException e) {
				Log.e(TAG, e.getMessage());
			} catch (ConnectionException e) {
				Toast.makeText(PreferencesActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
				Log.e(TAG, "mCheckCredentials Connection Exception: " + e.getMessage());
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_ACCOUNT_INVALID, 1, 0));
		}
	};
}
