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

package com.xorcode.andtweet.view;

import java.text.MessageFormat;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.xorcode.andtweet.R;
import com.xorcode.andtweet.net.Connection;

/**
 * @author torgny.bjers
 * 
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = "AndTweet.Preferences";

	public static final String KEY_TWITTER_USERNAME = "twitter_username";
	public static final String KEY_TWITTER_PASSWORD = "twitter_password";
	public static final String KEY_FETCH_FREQUENCY = "fetch_frequency";

	private ListPreference mFetchFrequencyPreference;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		mFetchFrequencyPreference = (ListPreference) getPreferenceScreen().findPreference(KEY_FETCH_FREQUENCY);
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
			String username = sharedPreferences.getString(KEY_TWITTER_USERNAME, "");
			String password = sharedPreferences.getString(KEY_TWITTER_PASSWORD, "");
			if (username.length() > 0 && password.length() > 0) {
				Connection c = new Connection(username, password);
				try {
					JSONObject jo = c.verifyCredentials();
					Log.d(TAG, jo.optString("id"));
					Log.d(TAG, jo.optString("user"));
				} catch (JSONException e) {
					Log.e(TAG, e.getMessage());
				}
			}
		}
	};
}
