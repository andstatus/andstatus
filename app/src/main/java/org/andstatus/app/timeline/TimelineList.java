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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.origin.Origin;
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
    private int sortFieldPrev = 0;
    private boolean sortDefault = true;
    private ViewGroup columnHeadersParent = null;
    private TimelineListContextMenu contextMenu = null;

    @Override
    protected void onPause() {
        myContext.persistentTimelines().saveChanged();
        super.onPause();
    }

    @Override
    public void onLoadFinished(boolean keepCurrentPosition) {
        showSortColumn();
        int sortFieldNew = sortDefault ? sortByField : 0 - sortByField;
        super.onLoadFinished(sortFieldPrev == sortFieldNew);
        sortFieldPrev = sortFieldNew;
    }

    private void showSortColumn() {
        for (int i = 0; i < columnHeadersParent.getChildCount(); i++) {
            View view = columnHeadersParent.getChildAt(i);
            if (!TextView.class.isAssignableFrom(view.getClass())) {
                continue;
            }
            TextView textView = (TextView) view;
            String text = textView.getText().toString();
            if (!TextUtils.isEmpty(text) && "▲▼↑↓".indexOf(text.charAt(0)) >= 0) {
                text = text.substring(1);
                textView.setText(text);
            }
            if (textView.getId() == sortByField) {
                textView.setText((sortDefault ? '▲' : '▼') + text);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.timeline_list;
        super.onCreate(savedInstanceState);

        contextMenu = new TimelineListContextMenu(this);
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linear_list_wrapper);
        LayoutInflater inflater = getLayoutInflater();
        View listHeader = inflater.inflate(R.layout.timeline_list_header, linearLayout, false);
        linearLayout.addView(listHeader, 0);

        columnHeadersParent = (ViewGroup) listHeader.findViewById(R.id.columnHeadersParent);
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
            sortDefault = !sortDefault;
        } else {
            sortDefault = true;
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
                                case R.id.account:
                                    result = compareString(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                                    if (result != 0) {
                                        break;
                                    }
                                    return compareString(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
                                case R.id.origin:
                                    result = compareString(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
                                    if (result != 0) {
                                        break;
                                    } else {
                                        result = compareString(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                                    }
                                    if (result != 0) {
                                        break;
                                    } else {
                                        result = compareString(lhs.timelineTitle.title, rhs.timelineTitle.title);
                                    }
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
                            int result = lhs == null ? 0 : lhs.compareTo(rhs);
                            return result == 0 ? 0 : sortDefault ? result : 0 - result;
                        }

                        private int compareSynced(TimelineListViewItem lhs, TimelineListViewItem rhs) {
                            int result = compareDate(lhs.timeline.getLastSyncedDate(), rhs.timeline.getLastSyncedDate());
                            if (result == 0) {
                                result = compareCheckbox(lhs.timeline.isSyncedAutomatically(), rhs.timeline.isSyncedAutomatically());
                            }
                            if (result == 0) {
                                result = compareCheckbox(lhs.timeline.isSyncable(), rhs.timeline.isSyncable());
                            }
                            return result;
                        }

                        private int compareDate(long lhs, long rhs) {
                            int result = lhs == rhs ? 0 : lhs > rhs ? 1 : -1;
                            return result == 0 ? 0 : !sortDefault ? result : 0 - result;
                        }

                        private int compareCheckbox(boolean lhs, boolean rhs) {
                            int result = lhs == rhs ? 0 : lhs ? 1 : -1;
                            return result == 0 ? 0 : !sortDefault ? result : 0 - result;
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
                view.setOnCreateContextMenuListener(contextMenu);
                view.setOnClickListener(this);
                setPosition(view, position);
                final TimelineListViewItem item = mItems.get(position);
                MyUrlSpan.showText(view, R.id.title, item.timelineTitle.title, false, true);
                MyAccount myAccount = item.timeline.getMyAccount();
                MyUrlSpan.showText(view, R.id.account, myAccount.isValid() ?
                        myAccount.toAccountButtonText(myContext) : "", false, true);
                Origin origin = item.timeline.getOrigin();
                MyUrlSpan.showText(view, R.id.origin, origin.isValid() ?
                        origin.getName() : "", false, true);
                MyCheckBox.show(view, R.id.displayedInSelector, item.timeline.isDisplayedInSelector(),
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                item.timeline.setDisplayedInSelector(isChecked);
                                MyLog.v("isDisplayedInSelector", (isChecked ? "+ " : "- ") + item.timeline);
                            }
                        });
                MyCheckBox.show(view, R.id.synced, item.timeline.isSyncedAutomatically(),
                        item.timeline.isSyncable() ?
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                        item.timeline.setSyncedAutomatically(isChecked);
                                        MyLog.v("isSyncedAutomatically", (isChecked ? "+ " : "- ") + item.timeline);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.timeline_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh_menu_item:
                showList(WhichPage.CURRENT);
                break;
            case R.id.reset_counters_menu_item:
                myContext.persistentTimelines().resetCounters();
                showList(WhichPage.CURRENT);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenu != null) {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }
}
