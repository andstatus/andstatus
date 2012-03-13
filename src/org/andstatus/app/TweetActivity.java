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

package org.andstatus.app;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * @author torgny.bjers
 *
 */
public class TweetActivity extends Activity {

	private static final String TAG = TweetActivity.class.getSimpleName();

	private static final String[] PROJECTION = new String[] {
		Msg._ID,
		User.AUTHOR_NAME,
		Msg.BODY,
		Msg.VIA,
		User.IN_REPLY_TO_NAME,
		Msg.IN_REPLY_TO_MSG_ID,
		Msg.CREATED_DATE
	};

	private Uri mUri;
	private Cursor mCursor;
	private TextView mAuthor;
	private TextView mMessage;
	private TextView mSentDate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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
			String aAuthor = mCursor.getString(mCursor.getColumnIndex(User.AUTHOR_NAME));
			String aMessage = mCursor.getString(mCursor.getColumnIndex(Msg.BODY));
			long aSentDate = mCursor.getLong(mCursor.getColumnIndex(Msg.CREATED_DATE));
			mAuthor.setText(aAuthor);
			mMessage.setLinksClickable(true);
			mMessage.setFocusable(true);
			mMessage.setFocusableInTouchMode(true);
			mMessage.setText(aMessage);
			Linkify.addLinks(mMessage, Linkify.ALL);
			String inReplyTo = "";
			int colIndex = mCursor.getColumnIndex(User.IN_REPLY_TO_NAME);
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
				RelativeTime.getDifference(this, aSentDate), 
				Html.fromHtml(mCursor.getString(mCursor.getColumnIndex(Msg.VIA))),
				inReplyTo
			);
			mSentDate.setText(sentDate);
		} else {
			Log.e(TAG, "No cursor found");
		}
	}

	/**
	 * Load the theme for preferences.
	 */
	protected void loadTheme() {
		boolean light = MyPreferences.getDefaultSharedPreferences().getBoolean("appearance_light_theme", false);
		StringBuilder theme = new StringBuilder();
		String name = MyPreferences.getDefaultSharedPreferences().getString("theme", "AndStatus");
		if (name.indexOf("Theme.") > -1) {
			name = name.substring(name.indexOf("Theme."));
		}
		theme.append("Theme.");
		if (light) {
			theme.append("Light.");
		}
		theme.append(name);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "loadTheme; theme=\"" + theme.toString() + "\"");
        }
		setTheme((int) getResources().getIdentifier(theme.toString(), "style", "org.andstatus.app"));
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appDataBundle = new Bundle();
		appDataBundle.putParcelable("content_uri", MyDatabase.Msg.SEARCH_URI);
		startSearch(null, false, appDataBundle, false);
		return true;
	}
}
