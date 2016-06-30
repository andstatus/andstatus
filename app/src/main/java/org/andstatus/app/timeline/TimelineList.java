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
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineList extends LoadableListActivity {
    private int sortByField = R.id.synced;
    private TriState sortDesc = TriState.UNKNOWN;

    @Override
    protected void onPause() {
        myContext.persistentTimelines().saveChanged();
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list_wide;
        super.onCreate(savedInstanceState);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linear_list_wrapper);
        LayoutInflater inflater = getLayoutInflater();
        View listHeader = inflater.inflate(R.layout.timeline_list_header, linearLayout, false);
        linearLayout.addView(listHeader, 0);

        ViewGroup columnHeadersParent = (ViewGroup) listHeader.findViewById(R.id.columnHeadersParent);
        for (int i = 0; i < columnHeadersParent.getChildCount(); i++) {
            View view = columnHeadersParent.getChildAt(i);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sortBy(v.getId());
                }
            });
        }
    }

    private void sortBy(int fieldId) {
        if (sortByField == fieldId) {
            sortDesc = sortDesc.not();
        } else {
            sortDesc = TriState.UNKNOWN;
        }
        sortByField = fieldId;
        showList(WhichPage.CURRENT);
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
                // TODO: Implement filter parameters in this activity
                for (Timeline timeline : myContext.persistentTimelines().
                        getFiltered(false, TriState.UNKNOWN, null, null)) {
                    TimelineListViewItem viewItem = new TimelineListViewItem(myContext, timeline);
                    mItems.add(viewItem);
                }
                if (sortByField != 0) {
                    Collections.sort(mItems, new Comparator<TimelineListViewItem>() {
                        @Override
                        public int compare(TimelineListViewItem lhs, TimelineListViewItem rhs) {
                            int result = 0;
                            switch (sortByField) {
                                case R.id.displayedInSelector:
                                    result = compareCheckbox(lhs.timeline.isDisplayedInSelector(), rhs.timeline.isDisplayedInSelector());
                                    if (result != 0) {
                                        break;
                                    }
                                case R.id.title:
                                    result = compareString(lhs.timelineTitle.title, rhs.timelineTitle.title);
                                    if (result != 0) {
                                        break;
                                    }
                                case R.id.subTitle:
                                    result = compareString(lhs.timelineTitle.subTitle, rhs.timelineTitle.subTitle);
                                    if (result != 0) {
                                        break;
                                    }
                                case R.id.synced:
                                    return compareSynced(lhs, rhs);
                                case R.id.syncSucceededDate:
                                case R.id.syncedTimesCount:
                                    result = compareDate(lhs.timeline.getSyncSucceededDate(), rhs.timeline.getSyncSucceededDate());
                                    if (result == 0) {
                                        return compareSynced(lhs, rhs);
                                    }
                                    break;
                                case R.id.syncFailedDate:
                                case R.id.syncFailedTimesCount:
                                    result = compareDate(lhs.timeline.getSyncFailedDate(), rhs.timeline.getSyncFailedDate());
                                    if (result == 0) {
                                        return compareSynced(lhs, rhs);
                                    }
                                    break;
                                case R.id.errorMessage:
                                    result = compareString(lhs.timeline.getErrorMessage(), rhs.timeline.getErrorMessage());
                                    if (result == 0) {
                                        return compareSynced(lhs, rhs);
                                    }
                                default:
                                    break;
                            }
                            return result;
                        }

                        private int compareString(String lhs, String rhs) {
                            if (sortDesc == TriState.UNKNOWN) {
                                sortDesc = TriState.FALSE;
                            }
                            int result = lhs == null ? 0 : lhs.compareTo(rhs);
                            return result == 0 ? 0 : sortDesc.toBoolean(false) ? 0 - result : result;
                        }

                        private int compareSynced(TimelineListViewItem lhs, TimelineListViewItem rhs) {
                            int result = compareDate(lhs.timeline.getLastSyncedDate(), rhs.timeline.getLastSyncedDate());
                            if (result == 0) {
                                result = compareCheckbox(lhs.timeline.isSynced(), rhs.timeline.isSynced());
                            }
                            if (result == 0) {
                                result = compareCheckbox(lhs.timeline.canBeSynced(), rhs.timeline.canBeSynced());
                            }
                            return result;
                        }

                        private int compareDate(long lhs, long rhs) {
                            if (sortDesc == TriState.UNKNOWN) {
                                sortDesc = TriState.TRUE;
                            }
                            int result = lhs == rhs ? 0 : lhs > rhs ? 1 : -1;
                            return result == 0 ? 0 : sortDesc.toBoolean(false) ? 0 - result : result;
                        }

                        private int compareCheckbox(boolean lhs, boolean rhs) {
                            if (sortDesc == TriState.UNKNOWN) {
                                sortDesc = TriState.TRUE;
                            }
                            int result = lhs == rhs ? 0 : lhs ? 1 : -1;
                            return result == 0 ? 0 : sortDesc.toBoolean(false) ? 0 - result : result;
                        }

                    });
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
                MyUrlSpan.showText(view, R.id.title, item.timelineTitle.title, false, true);
                MyUrlSpan.showText(view, R.id.subTitle, item.timelineTitle.subTitle, false, true);
                MyCheckBox.show(view, R.id.displayedInSelector, item.timeline.isDisplayedInSelector(),
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                item.timeline.setDisplayedInSelector(isChecked);
                                MyLog.v("isDisplayedInSelector", (isChecked ? "+ " : "- ") +
                                        item.timelineTitle.toString());
                            }
                        });
                MyCheckBox.show(view, R.id.synced, item.timeline.isSynced(),
                        item.timeline.canBeSynced() ?
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                        item.timeline.setSynced(isChecked);
                                        MyLog.v("isSynced", (isChecked ? "+ " : "- ") + item.timelineTitle);
                                }
                        } : null);
                MyUrlSpan.showText(view, R.id.syncedTimesCount, I18n.notZero(item.timeline.getSyncedTimesCount()), false, true);
                MyUrlSpan.showText(view, R.id.newItemsCount, I18n.notZero(item.timeline.getNewItemsCount()), false, true);
                MyUrlSpan.showText(view, R.id.syncSucceededDate,
                        RelativeTime.getDifference(TimelineList.this, item.timeline.getSyncSucceededDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.syncFailedTimesCount, I18n.notZero(item.timeline.getSyncFailedTimesCount()), false, true);
                MyUrlSpan.showText(view, R.id.syncFailedDate,
                        RelativeTime.getDifference(TimelineList.this, item.timeline.getSyncFailedDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.errorMessage, item.timeline.getErrorMessage(), false, true);
                return view;
            }

            private View newView() {
                return LayoutInflater.from(TimelineList.this).inflate(R.layout.timeline_list_item, null);
            }
        };
    }
}
