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
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.RelativeTime;

/**
 * Construct/format values of the rows for a Message item in a Timeline list
 * @author torgny.bjers
 */
public class TimelineViewBinder implements ViewBinder {
	/**
	 * @see android.widget.SimpleCursorAdapter.ViewBinder#setViewValue(android.view.View, android.database.Cursor, int)
	 * 
	 * To avoid BC_UNCONFIRMED_CAST warning instanceof is used before casting. 
	 * Unfortunately, findbugs doesn't understand isAssignableFrom
	 * see http://osdir.com/ml/java-findbugs-general/2010-03/msg00001.html
	 */
	@Override
    public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
		switch (view.getId()) {
    		case R.id.message_details:
                if ( view instanceof TextView) {
    	            setMessageDetails(cursor, columnIndex, (TextView) view);
    		    }
    			return true;
    		case R.id.avatar_image:
                if ( view instanceof ImageView) {
                    setAvatar(cursor, columnIndex, (ImageView) view);
                }
    			return true;
    		case R.id.message_favorited:
    		    if ( view instanceof ImageView) {
    		        setFavorited(cursor, (ImageView) view);
                }
    			return true;
    		default:
    		    break;
		}
		return false;
	}

    private void setMessageDetails(Cursor cursor, int columnIndex, TextView view) {
        String messageDetails = RelativeTime.getDifference(view.getContext(), cursor.getLong(columnIndex));
        int columnIndex2 = cursor.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID);
        if (columnIndex2 > -1) {
            long replyToMsgId = cursor.getLong(columnIndex2);
            if (replyToMsgId != 0) {
                String replyToName = "";
                columnIndex2 = cursor.getColumnIndex(User.IN_REPLY_TO_NAME);
                if (columnIndex2 > -1) {
                    replyToName = cursor.getString(columnIndex2);
                }
                if (TextUtils.isEmpty(replyToName)) {
                    replyToName = "...";
                }
                messageDetails += " " + String.format(Locale.getDefault(), view.getContext().getText(R.string.message_source_in_reply_to).toString(), replyToName);
            }
        }
        columnIndex2 = cursor.getColumnIndex(User.RECIPIENT_NAME);
        if (columnIndex2 > -1) {
            String recipientName = cursor.getString(columnIndex2);
            if (!TextUtils.isEmpty(recipientName)) {
                messageDetails += " " + String.format(Locale.getDefault(), view.getContext().getText(R.string.message_source_to).toString(), recipientName);
            }
        }
        view.setText(messageDetails);
    }

    private void setAvatar(Cursor cursor, int columnIndex, ImageView view) {
        int authorIdColumnIndex = cursor.getColumnIndex(Msg.AUTHOR_ID);
        long authorId = 0;
        if (authorIdColumnIndex > -1) {
            authorId = cursor.getLong(authorIdColumnIndex);
        }
        //int columnIndex = cursor.getColumnIndex(Avatar.FILE_NAME);
        String fileName = null;
        if (columnIndex > -1) {
            fileName = cursor.getString(columnIndex);
        }
        AvatarDrawable avatarDrawable = new AvatarDrawable(authorId, fileName);
        view.setImageDrawable(avatarDrawable.getDrawable());
    }
    
    private void setFavorited(Cursor cursor, ImageView view) {
        int columnIndex = cursor.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED);
        if (columnIndex > -1) {
            if (cursor.getInt(columnIndex) == 1) {
                view.setImageResource(android.R.drawable.star_on);
            } else {
                view.setImageResource(android.R.drawable.star_off);
            }
        }
    }
    
}
