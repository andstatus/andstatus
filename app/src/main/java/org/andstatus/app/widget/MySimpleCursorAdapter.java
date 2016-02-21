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
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MyTheme;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;

public class MySimpleCursorAdapter extends SimpleCursorAdapter {
    private final Context context;
    private final int layout;
    private final Cursor mCursor;
    private final boolean markReplies = MyPreferences.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false);

    private static final ThreadLocal<Boolean> ignoreGetItemId = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    
    @Override
    public long getItemId(int position) {
        if (mCursor == null) {
            return 0;
        } 
        if (mCursor.isClosed()) {
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
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getItemId, pos=" + position + " Caused java.lang.IllegalStateException")), e);
            return 0;
        } catch (StaleDataException e) {
            MyLog.w(this, MyLog.getStackTrace(new IllegalStateException("getItemId, pos=" + position + " Caused StaleDataException")), e);
            return 0;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        if (mCursor != null) {
            if (mCursor.isClosed()) {
                MyLog.w(this,
                        MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position
                                + " Closed cursor")));
            } else {
                try {
                    view = super.getView(position, convertView, parent);
                } catch (IllegalStateException e) {
                    MyLog.w(this,
                            MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position
                                    + " Caused java.lang.IllegalStateException")), e);
                } catch (StaleDataException e) {
                    MyLog.w(this,
                            MyLog.getStackTrace(new IllegalStateException("getView, pos=" + position
                                    + " Caused StaleDataException")), e);
                }
            }
        }
        if (view == null) {
            view = newEmptyView();
        }
        return view;
    }

    private View newEmptyView() {
        return View.inflate(context, layout, null); 
    }

    public MySimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to,
            int flags) {
        super(context, layout, c, from, to, flags);
        this.context = context;
        this.layout = layout;
        mCursor = c;
    }

    @Override
    protected void onContentChanged() {
        // Ignore at this level. Will be handled elsewhere...
    }

    @Override
    public void bindView(@NonNull View view, Context context, @NonNull Cursor cursor) {
        if (!markReplies || !markReply(view, cursor)) {
            view.setBackgroundResource(0);
            view.setPadding(0, 0, 0, 0);
        }
        super.bindView(view, context, cursor);
    }

    private boolean markReply(View view, Cursor cursor) {
        boolean backgroundSet = false;
        long replyToUserId = DbUtils.getLong(cursor, Msg.IN_REPLY_TO_USER_ID);
        if (replyToUserId != 0 &&
                MyContextHolder.get().persistentAccounts().fromUserId(replyToUserId).isValid()) {
            // For some reason, referring to the style drawable doesn't work
            // (to "?attr:replyBackground" )
            view.setBackgroundResource(MyTheme.isThemeLight()
                    ? R.drawable.reply_timeline_background_light
                    : R.drawable.reply_timeline_background);
            backgroundSet = true;
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
