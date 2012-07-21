/* 
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.RelativeTime;

/**
 * One message
 * @author torgny.bjers
 *
 */
public class TweetActivity extends Activity {

	private static final String TAG = TweetActivity.class.getSimpleName();

	private static final String[] PROJECTION = new String[] {
		Msg._ID,
		User.AUTHOR_NAME,
        User.SENDER_NAME,
		Msg.BODY,
		Msg.VIA,
		User.IN_REPLY_TO_NAME,
		Msg.IN_REPLY_TO_MSG_ID,
        User.RECIPIENT_NAME,
		Msg.CREATED_DATE
	};

	private Uri mUri;
	private Cursor mCursor;
	private TextView mAuthor;
	private TextView mMessage;
	private TextView mDetails;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        MyPreferences.loadTheme(TAG, this);

		setContentView(R.layout.tweetview);

		final Intent intent = getIntent();
		mUri = intent.getData();

		mAuthor = (TextView) findViewById(R.id.tweet_screen_name);
		mMessage = (TextView) findViewById(R.id.tweet_message);
		mDetails = (TextView) findViewById(R.id.tweet_sent);

		mCursor = managedQuery(mUri, PROJECTION, null, null, null);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mCursor != null) {
			mCursor.moveToFirst();
			String aAuthor = mCursor.getString(mCursor.getColumnIndex(User.AUTHOR_NAME));
            String aSender = mCursor.getString(mCursor.getColumnIndex(User.SENDER_NAME));
			String aMessage = mCursor.getString(mCursor.getColumnIndex(Msg.BODY));
			long createdDate = mCursor.getLong(mCursor.getColumnIndex(Msg.CREATED_DATE));
			mAuthor.setText(aAuthor);
			mMessage.setLinksClickable(true);
			mMessage.setFocusable(true);
			mMessage.setFocusableInTouchMode(true);
			mMessage.setText(aMessage);
			Linkify.addLinks(mMessage, Linkify.ALL);

            String messageDetails = RelativeTime.getDifference(this, createdDate); 

            String via = Html.fromHtml(mCursor.getString(mCursor.getColumnIndex(Msg.VIA))).toString();
            if (!MyPreferences.isEmpty(via)) {
                messageDetails += " " + String.format(
                        Locale.getDefault(),
                        getText(R.string.tweet_source_from).toString(),
                        via);
            }

            String replyToName = "";
            int colIndex = mCursor.getColumnIndex(User.IN_REPLY_TO_NAME);
            if (colIndex > -1) {
                replyToName = mCursor.getString(colIndex);
                if (!MyPreferences.isEmpty(replyToName)) {
                    messageDetails += " "
                            + String.format(Locale.getDefault(),
                                    getText(R.string.tweet_source_in_reply_to).toString(),
                                    replyToName);
                }
            }
            if (!MyPreferences.isEmpty(aSender)) {
                if (!aSender.equals(aAuthor)) {
                    if (!MyPreferences.isEmpty(replyToName)) {
                        messageDetails +=";";
                    }
                    messageDetails += " "
                            + String.format(Locale.getDefault(), getText(R.string.retweeted_by)
                                    .toString(), aSender);
                }
            }

            colIndex = mCursor.getColumnIndex(User.RECIPIENT_NAME);
            if (colIndex > -1) {
                String recipientName = mCursor.getString(colIndex);
                if (!MyPreferences.isEmpty(recipientName)) {
                    messageDetails += " " + String.format(Locale.getDefault(), getText(R.string.tweet_source_to).toString(), recipientName);
                }
            }
			
			mDetails.setText(messageDetails);
		} else {
			Log.e(TAG, "No cursor found");
		}
	}
}
