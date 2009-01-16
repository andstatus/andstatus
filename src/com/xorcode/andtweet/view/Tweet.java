/**
 * 
 */
package com.xorcode.andtweet.view;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.Log;
import android.widget.TextView;

import com.xorcode.andtweet.R;
import com.xorcode.andtweet.data.AndTweet.Tweets;
import com.xorcode.andtweet.util.RelativeTime;

/**
 * @author torgny.bjers
 *
 */
public class Tweet extends Activity {

	private static final String TAG = "AndTweet";

	private static final String[] PROJECTION = new String[] {
		Tweets._ID,
		Tweets.AUTHOR_ID,
		Tweets.MESSAGE,
		Tweets.SENT_DATE
	};

	private Uri mUri;
	private Cursor mCursor;
	private TextView mAuthor;
	private TextView mMessage;
	private TextView mSentDate;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final Intent intent = getIntent();
		mUri = intent.getData();

		setTheme(R.style.Theme_AndTweet_Large);

		setContentView(R.layout.tweetview);

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
			mSentDate.setText(RelativeTime.getDifference(aSentDate));
		} else {
			Log.w(TAG, "No cursor found");
		}
	}
}
