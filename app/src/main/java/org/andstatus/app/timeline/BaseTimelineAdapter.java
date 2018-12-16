/*
 * Copyright (C) 2015-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.support.annotation.NonNull;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.List;

public abstract class BaseTimelineAdapter<T extends ViewItem<T>> extends BaseAdapter  implements View.OnClickListener {
    protected final boolean showAvatars = MyPreferences.getShowAvatars();
    protected final boolean showAttachedImages = MyPreferences.getDownloadAndDisplayAttachedImages();
    protected final boolean markRepliesToMe = SharedPreferencesUtil.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_TO_ME_IN_TIMELINE, true);
    @NonNull
    protected final MyContext myContext;
    @NonNull
    private final TimelineData<T> listData;
    private final float displayDensity;
    private volatile boolean positionRestored = false;

    /** Single page data */
    public BaseTimelineAdapter(@NonNull MyContext myContext, @NonNull Timeline timeline, @NonNull List<T> items) {
        this(myContext,
                new TimelineData<T>(
                        null,
                        new TimelinePage<T>(new TimelineParameters(myContext, timeline, WhichPage.EMPTY), items)
                )
        );
    }

    public BaseTimelineAdapter(@NonNull MyContext myContext, @NonNull TimelineData<T> listData) {
        this.myContext = myContext;
        this.listData = listData;
        if (myContext.context() == null) {
            displayDensity = 1;
        } else {
            displayDensity = myContext.context().getResources().getDisplayMetrics().density;
            MyLog.v(this, () ->"density=" + displayDensity);
        }
    }

    @NonNull
    protected TimelineData<T> getListData() {
        return listData;
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    public T getItem(int position) {
        return listData.getItem(position);
    }

    public T getItem(View view) {
        return getItem(getPosition(view));
    }

    /** @return -1 if not found */
    public int getPosition(View view) {
        TextView positionView = getPositionView(view);
        if (positionView == null) {
            return -1;
        }
        return Integer.parseInt(positionView.getText().toString());
    }

    private TextView getPositionView(View view) {
        if (view == null) return null;
        View parentView = view;
        for (int i = 0; i < 10; i++) {
            TextView positionView = parentView.findViewById(R.id.position);
            if (positionView != null) {
                return positionView;
            }
            if (parentView.getParent() != null &&
                    View.class.isAssignableFrom(parentView.getParent().getClass())) {
                parentView = (View) parentView.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    protected void setPosition(View view, int position) {
        TextView positionView = getPositionView(view);
        if (positionView != null) {
            positionView.setText(Integer.toString(position));
        }
    }

    public int getPositionById(long itemId) {
        return listData.getPositionById(itemId);
    }

    public void setPositionRestored(boolean positionRestored) {
        this.positionRestored = positionRestored;
    }

    public boolean isPositionRestored() {
        return positionRestored;
    }

    protected boolean mayHaveYoungerPage() {
        return listData.mayHaveYoungerPage();
    }

    protected boolean isCombined() {
        return listData.params.isTimelineCombined();
    }

    @Override
    public void onClick(View v) {
        if (!MyPreferences.isLongPressToOpenContextMenu() && v.getParent() != null) {
            v.showContextMenu();
        }
    }

    // See  http://stackoverflow.com/questions/2238883/what-is-the-correct-way-to-specify-dimensions-in-dip-from-java-code
    protected int dpToPixes(int dp) {
        return (int) (dp * displayDensity);
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, listData);
    }
}