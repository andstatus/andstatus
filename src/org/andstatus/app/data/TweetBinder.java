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

package org.andstatus.app.data;

import java.util.Locale;

import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

import org.andstatus.app.R;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.RelativeTime;

/**
 * Construct/format values of the rows for a Message item in a Timeline list
 * @author torgny.bjers
 */
public class TweetBinder implements ViewBinder {
	/**
	 * @see android.widget.SimpleCursorAdapter.ViewBinder#setViewValue(android.view.View, android.database.Cursor, int)
	 */
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
		int colIndex = -1;
		switch (view.getId()) {
		case R.id.message_details:
			String messageDetails = RelativeTime.getDifference(view.getContext(), cursor.getLong(columnIndex));
			colIndex = cursor.getColumnIndex(User.IN_REPLY_TO_NAME);
			if (colIndex > -1) {
				String replyToName = cursor.getString(colIndex);
				if (!TextUtils.isEmpty(replyToName)) {
					messageDetails += " " + String.format(Locale.getDefault(), view.getContext().getText(R.string.message_source_in_reply_to).toString(), replyToName);
				}
			}
            colIndex = cursor.getColumnIndex(User.RECIPIENT_NAME);
            if (colIndex > -1) {
                String recipientName = cursor.getString(colIndex);
                if (!TextUtils.isEmpty(recipientName)) {
                    messageDetails += " " + String.format(Locale.getDefault(), view.getContext().getText(R.string.message_source_to).toString(), recipientName);
                }
            }
			((TextView)view).setText(messageDetails);
			return true;
		case R.id.tweet_avatar_image:
			return true;
		case R.id.tweet_favorite:
			colIndex = cursor.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED);
			if (colIndex > -1) {
				if (cursor.getInt(colIndex) == 1) {
					((ImageView)view).setImageResource(android.R.drawable.star_on);
				} else {
					((ImageView)view).setImageResource(android.R.drawable.star_off);
				}
			}
			return true;
		}
		return false;
	}
}
