/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.RelativeTime;

/**
 * Construct/format values of the rows for a Message item in a Timeline list
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
        boolean processed = true;
        switch (view.getId()) {
            case R.id.message_author:
                if ( view instanceof TextView) {
                    setMessageAuthor(cursor, columnIndex, (TextView) view);
                }
                break;
            case R.id.message_body:
                if ( view instanceof TextView) {
                    setMessageBody(cursor, columnIndex, (TextView) view);
                }
                break;
            case R.id.message_details:
                if ( view instanceof TextView) {
                    setMessageDetails(cursor, columnIndex, (TextView) view);
                }
                break;
            case R.id.avatar_image:
                if ( view instanceof ImageView) {
                    setAvatar(cursor, columnIndex, (ImageView) view);
                }
                break;
            case R.id.attached_image:
                if ( view instanceof ImageView) {
                    setAttachedImage(cursor, columnIndex, (ImageView) view);
                }
                break;
            case R.id.message_favorited:
                if ( view instanceof ImageView) {
                    setFavorited(cursor, (ImageView) view);
                }
                break;
            case R.id.id:
                if ( view instanceof TextView) {
                    setMessageId(cursor, columnIndex, (TextView) view);
                }
                break;
            default:
                processed = false;
                break;
        }
        return processed;
    }

    private void setMessageAuthor(Cursor cursor, int columnIndex, TextView view) {
        view.setText(TimelineSql.userColumnIndexToNameAtTimeline(cursor, columnIndex, MyPreferences.showOrigin()));
    }

    private void setMessageId(Cursor cursor, int columnIndex, TextView view) {
        if (columnIndex >= 0) {
            String id = cursor.getString(columnIndex);
            if (id != null) {
                view.setText(id);
            }
        }
    }

    private void setMessageBody(Cursor cursor, int columnIndex, TextView view) {
        if (columnIndex >= 0) {
            String body = cursor.getString(columnIndex);
            if (body != null) {
                view.setText(Html.fromHtml(body));
            }
        }
    }
    
    private void setMessageDetails(Cursor cursor, int columnIndex, TextView view) {
        String messageDetails = RelativeTime.getDifference(view.getContext(), cursor.getLong(columnIndex));
        int columnIndex2 = cursor.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID);
        if (columnIndex2 >= 0) {
            long replyToMsgId = cursor.getLong(columnIndex2);
            if (replyToMsgId != 0) {
                String replyToName = "";
                columnIndex2 = cursor.getColumnIndex(User.IN_REPLY_TO_NAME);
                if (columnIndex2 >= 0) {
                    replyToName = cursor.getString(columnIndex2);
                }
                if (TextUtils.isEmpty(replyToName)) {
                    replyToName = "...";
                }
                messageDetails += " " + String.format(MyContextHolder.get().getLocale(), view.getContext().getText(R.string.message_source_in_reply_to).toString(), replyToName);
            }
        }
        columnIndex2 = cursor.getColumnIndex(User.RECIPIENT_NAME);
        if (columnIndex2 >= 0) {
            String recipientName = cursor.getString(columnIndex2);
            if (!TextUtils.isEmpty(recipientName)) {
                messageDetails += " " + String.format(MyContextHolder.get().getLocale(), view.getContext().getText(R.string.message_source_to).toString(), recipientName);
            }
        }
        view.setText(messageDetails);
    }

    private void setAvatar(Cursor cursor, int columnIndex, ImageView view) {
        int authorIdColumnIndex = cursor.getColumnIndex(Msg.AUTHOR_ID);
        long authorId = 0;
        if (authorIdColumnIndex >= 0) {
            authorId = cursor.getLong(authorIdColumnIndex);
        }
        String fileName = null;
        if (columnIndex >= 0) {
            fileName = cursor.getString(columnIndex);
        }
        AvatarDrawable avatarDrawable = new AvatarDrawable(authorId, fileName);
        view.setImageDrawable(avatarDrawable.getDrawable());
    }

    private void setAttachedImage(Cursor cursor, int columnIndex, ImageView view) {
        Drawable drawable = AttachedImageDrawable.drawableFromCursor(cursor);
        if (drawable != null) {
            view.setVisibility(View.VISIBLE);
            view.setImageDrawable(drawable);
        } else {
            view.setVisibility(View.GONE);
        }
    }
    
    private void setFavorited(Cursor cursor, ImageView view) {
        int columnIndex = cursor.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED);
        if (columnIndex >= 0) {
            if (cursor.getInt(columnIndex) == 1) {
                view.setImageResource(android.R.drawable.star_on);
            } else {
                view.setImageResource(android.R.drawable.star_off);
            }
        }
    }
    
}
