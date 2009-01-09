/**
 * 
 */
package com.xorcode.andtweet.view;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.xorcode.andtweet.R;
import com.xorcode.andtweet.data.AndTweet.Tweets;

/**
 * @author tbjers
 *
 */
public class Tweet extends Activity {

	private static final String TAG = "Tweet";
	
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
		final String action = intent.getAction();
		mUri = intent.getData();

		setContentView(R.layout.tweetview);

		mAuthor = (TextView) findViewById(R.id.tweet_screen_name);
		mMessage = (TextView) findViewById(R.id.tweet_message);
		mSentDate = (TextView) findViewById(R.id.tweet_sent);

		mCursor = managedQuery(mUri, PROJECTION, null, null, null);
	}

	protected void onResume() {
		super.onResume();

		if (mCursor != null) {
			mCursor.moveToFirst();
			String aAuthor = mCursor.getString(mCursor.getColumnIndex(Tweets.AUTHOR_ID));
			String aMessage = mCursor.getString(mCursor.getColumnIndex(Tweets.MESSAGE));
			long aSentDate = mCursor.getLong(mCursor.getColumnIndex(Tweets.SENT_DATE));
			mAuthor.setText(aAuthor);
			Spannable sText = new SpannableString(aMessage);
			Linkify.addLinks(sText, Linkify.ALL);
			mMessage.setText(sText);
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(aSentDate);
			DateFormat df = new SimpleDateFormat(getText(R.string.http_dateformat).toString());
			mSentDate.setText(df.format(cal.getTime()));
		}
	}
}
