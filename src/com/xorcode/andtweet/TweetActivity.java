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

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.util.RelativeTime;

/**
 * @author torgny.bjers
 *
 */
public class TweetActivity extends Activity {

	private static final String TAG = "AndTweetDatabase";

	private static final String[] PROJECTION = new String[] {
		Tweets._ID,
		Tweets.AUTHOR_ID,
		Tweets.MESSAGE,
		Tweets.SOURCE,
		Tweets.IN_REPLY_TO_AUTHOR_ID,
		Tweets.IN_REPLY_TO_STATUS_ID,
		Tweets.SENT_DATE
	};

	private Uri mUri;
	private Cursor mCursor;
	private TextView mAuthor;
	private TextView mMessage;
	private TextView mSentDate;
	private SharedPreferences mSP;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		mSP = PreferenceManager.getDefaultSharedPreferences(this);

		loadTheme();

		setContentView(R.layout.tweetview);

		final Intent intent = getIntent();
		mUri = intent.getData();

		mAuthor = (TextView) findViewById(R.id.tweet_screen_name);
		mMessage = (TextView) findViewById(R.id.tweet_message);
		mSentDate = (TextView) findViewById(R.id.tweet_sent);

		mCursor = managedQuery(mUri, PROJECTION, null, null, null);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mCursor != null) {
			mCursor.moveToFirst();
			String aAuthor = mCursor.getString(mCursor.getColumnIndex(Tweets.AUTHOR_ID));
			String aMessage = mCursor.getString(mCursor.getColumnIndex(Tweets.MESSAGE));
			long aSentDate = mCursor.getLong(mCursor.getColumnIndex(Tweets.SENT_DATE));
			mAuthor.setText(aAuthor);
			mMessage.setLinksClickable(true);
			mMessage.setFocusable(true);
			mMessage.setFocusableInTouchMode(true);
			mMessage.setText(aMessage);
			Linkify.addLinks(mMessage, Linkify.ALL);
			String inReplyTo = "";
			int colIndex = mCursor.getColumnIndex(Tweets.IN_REPLY_TO_AUTHOR_ID);
			if (colIndex > -1) {
				inReplyTo = mCursor.getString(colIndex);
				if (inReplyTo != null && "null".equals(inReplyTo) == false) {
					inReplyTo = String.format(Locale.getDefault(), getText(R.string.tweet_source_in_reply_to).toString(), inReplyTo);
				}
			}
			if (inReplyTo == null || "null".equals(inReplyTo)) inReplyTo = "";
			String sentDate = String.format(
				Locale.getDefault(), 
				getText(R.string.tweet_source_from).toString(), 
				RelativeTime.getDifference(aSentDate), 
				Html.fromHtml(mCursor.getString(mCursor.getColumnIndex(Tweets.SOURCE))),
				inReplyTo
			);
			mSentDate.setText(sentDate);
		} else {
			Log.w(TAG, "No cursor found");
		}
	}

	/**
	 * Load the theme for preferences.
	 */
	protected void loadTheme() {
		boolean light = mSP.getBoolean("appearance_light_theme", false);
		StringBuilder theme = new StringBuilder();
		String name = mSP.getString("theme", "AndTweet");
		if (name.indexOf("Theme.") > -1) {
			name = name.substring(name.indexOf("Theme."));
		}
		theme.append("Theme.");
		if (light) {
			theme.append("Light.");
		}
		theme.append(name);
		setTheme((int) getResources().getIdentifier(theme.toString(), "style", "com.xorcode.andtweet"));
	}
}
