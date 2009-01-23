/**
 * 
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
		int resourceId = getResources().getIdentifier(mSP.getString("theme", "Theme.AndTweet"), "style", "com.xorcode.andtweet");
		setTheme(resourceId);
	}
}
