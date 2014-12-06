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

package org.andstatus.app.widget;

import android.content.Context;
import android.database.Cursor;
import android.database.StaleDataException;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;

public class MySimpleCursorAdapter extends SimpleCursorAdapter {
    private Context context;
    private int layout;
    private Cursor mCursor;
    
    private static ThreadLocal<Boolean> ignoreGetItemId = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    
    @Override
    public long getItemId(int position) {
        if (mCursor == null) {
            return 0;
        } if (mCursor.isClosed()) {
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getItemId, pos=" + position + " Closed cursor")));
            return 0;
        }
        if (ignoreGetItemId.get()) {
            MyLog.i(this, "Ignoring getItemId");
            return 0;
        }
        try {
            return super.getItemId(position);
        } catch (IllegalStateException e) {
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getItemId, pos=" + position + " Caused java.lang.IllegalStateException")));
            return 0;
        } catch (StaleDataException e) {
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getItemId, pos=" + position + " Caused StaleDataException")));
            return 0;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        if (mCursor == null) {
            // Do nothing
        } else if (mCursor.isClosed()) {
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position + " Closed cursor")));
        } else {
            try {
                view = super.getView(position, convertView, parent);
            } catch (IllegalStateException e) {
                MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position + " Caused java.lang.IllegalStateException")));
            } catch (StaleDataException e) {
                MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position + " Caused StaleDataException")));
            }
        }
        if (view == null) {
            view = getEmptyView(position, convertView, parent);
        }
        return view;
    }

    private View getEmptyView(int position, View convertView, ViewGroup parent) {
        return View.inflate(context, layout, null); 
    }

    public MySimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
            int flags) {
        super(context, layout, c, from, to, flags);
        this.context = context;
        this.layout = layout;
        mCursor = c;
    }

    private boolean markReplies = MyPreferences.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false);
    

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

    public static void setBackgroundCompat(View view, Drawable drawable) {
        view.setBackground(drawable); 
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

    public static void beforeSwapCursor() {
        ignoreGetItemId.set(true);
    }

    public static void afterSwapCursor() {
        ignoreGetItemId.set(false);
    }

}
