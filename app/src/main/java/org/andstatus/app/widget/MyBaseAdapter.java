/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.msg.DuplicationLink;
import org.andstatus.app.msg.TimelineViewItem;
import org.andstatus.app.util.MyLog;

public abstract class MyBaseAdapter extends BaseAdapter  implements View.OnClickListener {

    protected final float displayDensity;
    private volatile boolean positionRestored = false;
    protected final MyContext myContext;

    public MyBaseAdapter(MyContext myContext) {
        this.myContext = myContext;
        displayDensity = myContext.context().getResources().getDisplayMetrics().density;
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,"density=" + displayDensity);
        }
    }

    public Object getItem(View view) {
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
        if (view != null) {
            View parentView = view;
            for (int i = 0; i < 10; i++) {
                TextView positionView = (TextView) parentView.findViewById(R.id.position);
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
        }
        return null;
    }

    protected void setPosition(View view, int position) {
        TextView positionView = getPositionView(view);
        if (positionView != null) {
            positionView.setText(Integer.toString(position));
        }
    }

    public void setCollapseDuplicates(boolean collapse, long itemId) {
        notifyDataSetChanged();
    }

    public boolean isCollapseDuplicates() {
        return false;
    }

    public int getPositionById(long itemId) {
        if (itemId != 0) {
            for (int position = 0; position < getCount(); position++) {
                if (getItemId(position) == itemId) {
                    return position;
                }
            }
        }
        return -1;
    }

    public void setPositionRestored(boolean positionRestored) {
        this.positionRestored = positionRestored;
    }

    public boolean isPositionRestored() {
        return positionRestored;
    }

    @Override
    public void onClick(View v) {
        if (!MyPreferences.isLongPressToOpenContextMenu()) {
            v.showContextMenu();
        }
    }

    // See  http://stackoverflow.com/questions/2238883/what-is-the-correct-way-to-specify-dimensions-in-dip-from-java-code
    protected int dpToPixes(int dp) {
        return (int) (dp * displayDensity);
    }

    public boolean canBeCollapsed(long itemId) {
        int position = getPositionById(itemId);
        if (position >= 0) {
            Object item = getItem(position);
            if (item != null && TimelineViewItem.class.isAssignableFrom(item.getClass())) {
                TimelineViewItem viewItem = (TimelineViewItem) item;
                if (position > 0) {
                    if (viewItem.duplicates((TimelineViewItem) getItem(position - 1)) != DuplicationLink.NONE) {
                        return true;
                    }
                }
                if (viewItem.duplicates((TimelineViewItem) getItem(position + 1)) != DuplicationLink.NONE) {
                    return true;
                }
            }
        }
        return false;
    }
}