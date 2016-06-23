/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineList extends LoadableListActivity {

    @Override
    protected void onPause() {
        myContext.persistentTimelines().saveChanged();
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list;
        super.onCreate(savedInstanceState);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linear_list_wrapper);
        LayoutInflater inflater = getLayoutInflater();
        View listHeader = inflater.inflate(R.layout.timeline_list_header, linearLayout, false);
        linearLayout.addView(listHeader, 0);
    }

    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        final List<TimelineListViewItem> mItems = new ArrayList<>();

        return new SyncLoader() {
            @Override
            public void allowLoadingFromInternet() {

            }

            @Override
            public void load(ProgressPublisher publisher) {
                for (Timeline timeline : myContext.persistentTimelines().getFiltered(false, true, null, null)) {
                    TimelineListViewItem viewItem = new TimelineListViewItem(
                            timeline,
                            TimelineTitle.load(timeline, MyAccount.getEmpty()));
                    mItems.add(viewItem);
                }
            }

            @Override
            public List<? extends Object> getList() {
                return mItems;
            }

            @Override
            public int size() {
                return mItems.size();
            }
        };
    }

    @Override
    protected MyBaseAdapter newListAdapter() {

        return new MyBaseAdapter() {
            final List<TimelineListViewItem> mItems;
            {
                mItems = (List<TimelineListViewItem>) getLoaded().getList();
            }

            @Override
            public int getCount() {
                return mItems.size();
            }

            @Override
            public Object getItem(int position) {
                if (position < 0 || position >= getCount()) {
                    return null;
                }
                return mItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                if (position < 0 || position >= getCount()) {
                    return 0;
                }
                return mItems.get(position).timeline.getId();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView == null ? newView() : convertView;
                view.setOnClickListener(this);
                setPosition(view, position);
                final TimelineListViewItem item = mItems.get(position);
                MyUrlSpan.showText(view, R.id.title, item.timelineTitle.title, false);
                MyUrlSpan.showText(view, R.id.subTitle,  item.timelineTitle.subTitle, false);
                MyUrlSpan.showCheckBox(view, R.id.synced, item.timeline.isSynced(),
                        item.timeline.isSyncable() ? new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                boolean isChecked = ((CheckBox) v).isChecked();
                                item.timeline.setSynced(isChecked);
                                MyLog.v("isSynced", (isChecked ? "+ " : "- ") + item.timelineTitle);
                            }
                        } : null);
                MyUrlSpan.showText(view, R.id.syncedDate, StringUtils.notEmpty(
                        RelativeTime.getDifference(TimelineList.this, item.timeline.getSyncedDate()), " "), false);
                MyUrlSpan.showCheckBox(view, R.id.displayedInSelector, item.timeline.isDisplayedInSelector(),
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                boolean isChecked = ((CheckBox) v).isChecked();
                                item.timeline.setDisplayedInSelector(isChecked);
                                MyLog.v("isDisplayedInSelector", (isChecked ? "+ " : "- ") + item.timelineTitle.toString());
                            }
                });
                return view;
            }

            private View newView() {
                return LayoutInflater.from(TimelineList.this).inflate(R.layout.timeline_list_item, null);
            }
        };
    }
}
