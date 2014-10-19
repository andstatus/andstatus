package org.andstatus.app;

import android.content.CursorLoader;

public class TimelineCursorLoader2 extends CursorLoader {

    public TimelineCursorLoader2(TimelineListParameters params) {
        super(params.mContext, params.mContentUri, params.mProjection, params.mSa.selection, params.mSa.selectionArgs, params.mSortOrder);
    }

}
