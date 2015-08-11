/* 
 * Copyright (c) 2011-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.app.Activity;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineViewBinder;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.widget.MySimpleCursorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineFragment extends ListFragment implements
        OnScrollListener, OnItemClickListener {
    private static final int DIALOG_ID_TIMELINE_TYPE = 9;
    private static final int LOADER_ID = 1;
    private static final String ACTIVITY_PERSISTENCE_NAME = TimelineFragment.class.getSimpleName();

    private TimelineActivity mTimeline;

    /**
     * Visibility of the layout indicates whether Messages are being loaded into the list (asynchronously...)
     * The layout appears at the bottom of the list of messages
     * when new items are being loaded into the list
     */
    private LinearLayout mLoadingLayout;

    /**
     * Is saved position restored (or some default positions set)?
     */
    private boolean mPositionRestored = false;

    private boolean mIsLoading = false;

    @Override
    public void onAttach(Activity activity) {
        mTimeline = (TimelineActivity) activity;
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        MyLog.v(this, "onDetach");
        mTimeline = null;
        super.onDetach();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.my_list_fragment, container, false);
        mLoadingLayout = (LinearLayout) inflater.inflate(R.layout.item_loading, null);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getListView().addFooterView(mLoadingLayout);

        getListView().setOnScrollListener(this);
        getListView().setOnItemClickListener(this);

        createListAdapter(getEmptyCursor());

        getListView().setOnCreateContextMenuListener(mTimeline.getContextMenu());
        super.onActivityCreated(savedInstanceState);
    }

    private void saveListPosition() {
        if (mTimeline != null) {
            mTimeline.saveListPosition();
        }
    }

    @Override
    public void onPause() {
        final String method = "onPause";
        super.onPause();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method);
        }

        if (mPositionRestored) {
            setFastScrollThumb(false);
            if (!isLoading()) {
                saveListPosition();
            }
            mPositionRestored = false;
        }
    }

    private void setFastScrollThumb(boolean isVisible) {
        getListView().setFastScrollEnabled(isVisible);
    }

    // That advice doesn't fit here:
    // see http://stackoverflow.com/questions/5996885/how-to-wait-for-android-runonuithread-to-be-finished
    protected void savePositionOnUiThread() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mPositionRestored) {
                    saveListPosition();
                }
            }
        };
        if (mTimeline != null) {
            mTimeline.runOnUiThread(runnable);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, final View view, final int position, final long id) {
        if (mTimeline != null) {
            mTimeline.onItemClick(adapterView, view, position, id);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        if (mTimeline != null && mPositionRestored && !isLoading()) {
            mTimeline.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                break;
            case OnScrollListener.SCROLL_STATE_FLING:
                setFastScrollThumb(true);
                break;
            default:
                break;
        }
    }

    private void createListAdapter(Cursor cursor) {
        List<String> columnNames = new ArrayList<String>();
        List<Integer> viewIds = new ArrayList<Integer>();
        columnNames.add(User.AUTHOR_NAME);
        viewIds.add(R.id.message_author);
        columnNames.add(MyDatabase.Msg.BODY);
        viewIds.add(R.id.message_body);
        columnNames.add(MyDatabase.Msg.CREATED_DATE);
        viewIds.add(R.id.message_details);
        columnNames.add(MyDatabase.MsgOfUser.FAVORITED);
        viewIds.add(R.id.message_favorited);
        columnNames.add(MyDatabase.Msg._ID);
        viewIds.add(R.id.id);
        int listItemLayoutId = R.layout.message_basic;
        if (MyPreferences.showAvatars()) {
            listItemLayoutId = R.layout.message_avatar;
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
            viewIds.add(R.id.avatar_image);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(MyDatabase.Download.IMAGE_ID);
            viewIds.add(R.id.attached_image);
        }
        MySimpleCursorAdapter mCursorAdapter = new MySimpleCursorAdapter(mTimeline,
                listItemLayoutId, cursor, columnNames.toArray(new String[]{}),
                toIntArray(viewIds), 0);
        mCursorAdapter.setViewBinder(new TimelineViewBinder());

        setListAdapter(mCursorAdapter);
    }

    private Cursor getEmptyCursor() {
        return new MatrixCursor(TimelineSql.getTimelineProjection());
    }

    /**
     * See http://stackoverflow.com/questions/960431/how-to-convert-listinteger-to-int-in-java
     */
    private static int[] toIntArray(List<Integer> list){
        int[] ret = new int[list.size()];
        for(int i = 0;i < ret.length;i++) {
            ret[i] = list.get(i);
        }
        return ret;
    }

    protected boolean isLoading() {
        return mIsLoading;
    }

    protected void setLoading(String source, boolean isLoading) {
        if (isLoading() != isLoading && mTimeline != null && !mTimeline.isFinishing()) {
            mIsLoading = isLoading;
            MyLog.v(this, source + " set isLoading to " + isLoading);
            mLoadingLayout.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        }
    }

    protected void restoreListPosition(TimelineListParameters mListParameters) {
        mPositionRestored = new TimelineListPositionStorage(getListAdapter(), getListView(), mListParameters).restoreListPosition();
    }

    public boolean isPositionRestored() {
        return mPositionRestored;
    }
}
