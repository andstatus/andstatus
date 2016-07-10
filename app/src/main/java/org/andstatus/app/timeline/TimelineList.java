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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.EnumSelector;
import org.andstatus.app.IntentExtra;
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
    private TimelineListViewItem selectedItem = null;
    private boolean isTotalCounters = false;
    private volatile long countersSince = 0;

    @Override
    protected void onPause() {
        myContext.persistentTimelines().saveChanged();
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_DISPLAYED_IN_SELECTOR:
                if (selectedItem != null) {
                    final DisplayedInSelector displayedInSelector = DisplayedInSelector.load(
                            data.getStringExtra(IntentExtra.SELECTABLE_ENUM.key));
                    selectedItem.timeline.setDisplayedInSelector(displayedInSelector);
                    MyLog.v("isDisplayedInSelector", displayedInSelector.save() + " " +
                            selectedItem.timeline);
                    if (displayedInSelector != DisplayedInSelector.IN_CONTEXT || sortByField == R.id.displayedInSelector) {
                        showList(WhichPage.CURRENT);
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
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
                countersSince = 0;
                for (Timeline timeline : myContext.persistentTimelines().
                        getFiltered(false, TriState.UNKNOWN, null, null)) {
                    TimelineListViewItem viewItem = new TimelineListViewItem(myContext, timeline);
                    if (viewItem.timeline.getCountSince() > 0 && viewItem.timeline.getCountSince() > countersSince) {
                        countersSince = viewItem.timeline.getCountSince();
                    }
                    mItems.add(viewItem);
                }
                if (sortByField != 0) {
                    Collections.sort(mItems, new TimelineListViewItemComparator(sortByField, sortDefault));
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
            Timeline defaultTimeline = myContext.persistentTimelines().getDefault();

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
                showDisplayedInSelector(view, item);
                MyCheckBox.show(view, R.id.synced, item.timeline.isSyncedAutomatically(),
                        item.timeline.isSyncable() ?
                                new CompoundButton.OnCheckedChangeListener() {
                                    @Override
                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                        item.timeline.setSyncedAutomatically(isChecked);
                                        MyLog.v("isSyncedAutomatically", (isChecked ? "+ " : "- ") + item.timeline);
                                }
                        } : null);
                MyUrlSpan.showText(view, R.id.syncedTimesCount, I18n.notZero(
                        isTotalCounters ? item.timeline.getSyncedTimesCountTotal() : item.timeline.getSyncedTimesCount()), false, true);
                MyUrlSpan.showText(view, R.id.downloadedItemsCount, I18n.notZero(
                        isTotalCounters ? item.timeline.getDownloadedItemsCountTotal() : item.timeline.getDownloadedItemsCount()), false, true);
                MyUrlSpan.showText(view, R.id.newItemsCount, I18n.notZero(
                        isTotalCounters ? item.timeline.getNewItemsCountTotal() : item.timeline.getNewItemsCount()), false, true);
                MyUrlSpan.showText(view, R.id.syncSucceededDate,
                        RelativeTime.getDifference(TimelineList.this, item.timeline.getSyncSucceededDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.syncFailedTimesCount, I18n.notZero(
                        isTotalCounters ? item.timeline.getSyncFailedTimesCountTotal() : item.timeline.getSyncFailedTimesCount()), false, true);
                MyUrlSpan.showText(view, R.id.syncFailedDate,
                        RelativeTime.getDifference(TimelineList.this, item.timeline.getSyncFailedDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.errorMessage, item.timeline.getErrorMessage(), false, true);
                return view;
            }

            protected void showDisplayedInSelector(View parentView, final TimelineListViewItem item) {
                CheckBox view = (CheckBox) parentView.findViewById(R.id.displayedInSelector);
                MyCheckBox.show(parentView, R.id.displayedInSelector, item.timeline.isDisplayedInSelector() != DisplayedInSelector.NEVER,
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                if (isChecked) {
                                    selectedItem = item;
                                    EnumSelector.newInstance(
                                            ActivityRequestCode.SELECT_DISPLAYED_IN_SELECTOR,
                                            DisplayedInSelector.class).show(TimelineList.this);
                                } else {
                                    item.timeline.setDisplayedInSelector(DisplayedInSelector.NEVER);
                                    buttonView.setText("");
                                    MyLog.v("isDisplayedInSelector", (isChecked ? "+ " : "- ") + item.timeline);
                                }
                            }
                        });
                view.setText(item.timeline.equals(defaultTimeline) ? "D" :
                        (item.timeline.isDisplayedInSelector() == DisplayedInSelector.ALWAYS ? "*" : ""));
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
    protected CharSequence getCustomTitle() {
        StringBuilder title = new StringBuilder(getTitle() + " / ");
        if (isTotalCounters) {
            title.append(getText(R.string.total_counters));
        } else if (countersSince > 0) {
            title.append(String.format(getText(R.string.since).toString(),
                    RelativeTime.getDifference(this, countersSince)));
        }
        return title;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh_menu_item:
                showList(WhichPage.CURRENT);
                return true;
            case R.id.reset_counters_menu_item:
                myContext.persistentTimelines().resetCounters(isTotalCounters);
                showList(WhichPage.CURRENT);
                return true;
            case R.id.total_counters:
                isTotalCounters = !isTotalCounters;
                item.setChecked(isTotalCounters);
                showList(WhichPage.CURRENT);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenu != null) {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }

}
