/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.support.android.v11.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.SimpleCursorAdapter;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;

/**
 * TODO: Needed only to get rid of auto-requery. Starting from API 11 we may use another constructor,
 * which allows to disable requery in its "flags" parameters.
 * @author yvolk@yurivolkov.com
 */
public class MySimpleCursorAdapter extends SimpleCursorAdapter {
    private boolean markReplies = MyPreferences.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false);
    
    public MySimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
            int flags) {
        super(context, layout, c, from, to);
    }

    @Override
    protected void onContentChanged() {
        // Ignore at this level. Will be handled elsewhere...
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!markReplies || !markReply(view, context, cursor)) {
            setBackgroundCompat(view, null);
            view.setPadding(0, 0, 0, 0);
        }
        super.bindView(view, context, cursor);
    }

    @SuppressLint("NewApi")
    public static void setBackgroundCompat(View view, Drawable drawable) {
        if (android.os.Build.VERSION.SDK_INT >= 16)  {
            view.setBackground(drawable); 
        } else {
            view.setBackgroundDrawable(drawable);
        }
    }

    private boolean markReply(View view, Context context, Cursor cursor) {
        boolean backgroundSet = false;
        int columnIndex2 = cursor.getColumnIndex(Msg.IN_REPLY_TO_USER_ID);
        if (columnIndex2 >= 0) {
            long replyToUserId = cursor.getLong(columnIndex2);
            if (replyToUserId != 0
                    && MyContextHolder.get().persistentAccounts().fromUserId(replyToUserId) != null) {
                // For some reason, referring to the style drawable doesn't work
                // (to "?attr:replyBackground" )
                setBackgroundCompat(view, context.getResources().getDrawable(
                        MyPreferences.isThemeLight() ? R.drawable.reply_timeline_background_light
                                : R.drawable.reply_timeline_background));
                backgroundSet = true;
            }
        }
        return backgroundSet;
    }

}
