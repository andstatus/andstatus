/* 
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

import java.util.Arrays;

import org.andstatus.app.util.MyLog;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.widget.FilterQueryProvider;
import android.widget.SimpleCursorAdapter;

/**
 * @author torgny.bjers
 *
 */
public class PagedCursorAdapter extends SimpleCursorAdapter implements FilterQueryProvider {

    private static final String TAG = PagedCursorAdapter.class.getSimpleName();

    private ContentResolver mContentResolver;
    private Uri mUri;
    private String[] mProjection;
    private String mSortOrder;

    /**
     * 
     * @param context
     * @param layout
     * @param c
     * @param from
     * @param to
     */
    public PagedCursorAdapter(Context context, int layout, Cursor c,
            String[] from, int[] to, Uri uri, String[] projection, String sortOrder) {
        super(context, layout, c, from, to);
        mContentResolver = context.getContentResolver();
        mUri = uri;
        mProjection = projection.clone();
        mSortOrder = sortOrder;
        setFilterQueryProvider(this);
    }

    @Override
    public Cursor runQuery(CharSequence constraint) {
        if (constraint != null) {
            if (mSortOrder.indexOf("LIMIT 0,") > 0) {
                String newSortOrder = mSortOrder.substring(0, mSortOrder.indexOf("LIMIT 0,"));
                mSortOrder = newSortOrder;
            }
            mSortOrder += " " + constraint.toString().trim();
        }
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "runQuery, mUri=" + mUri + "; mProjection=" + Arrays.toString(mProjection) + "; mSortOrder=" + mSortOrder + ";");
        }
        return mContentResolver.query(mUri, mProjection, null, null, mSortOrder);
    }
}
