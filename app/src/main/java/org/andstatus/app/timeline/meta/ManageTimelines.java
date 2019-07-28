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

package org.andstatus.app.timeline.meta;

import android.content.Intent;
import android.os.Bundle;
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
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListPosition;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyCheckBox;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.view.EnumSelector;

import java.util.stream.Collectors;

/**
 * @author yvolk@yurivolkov.com
 */
public class ManageTimelines extends LoadableListActivity {
    private int sortByField = R.id.synced;
    private boolean sortDefault = true;
    private ViewGroup columnHeadersParent = null;
    private ManageTimelinesContextMenu contextMenu = null;
    private ManageTimelinesViewItem selectedItem = null;
    private boolean isTotal = false;
    private volatile long countersSince = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.timeline_list;
        super.onCreate(savedInstanceState);

        contextMenu = new ManageTimelinesContextMenu(this);
        LinearLayout linearLayout = findViewById(R.id.linear_list_wrapper);
        LayoutInflater inflater = getLayoutInflater();
        View listHeader = inflater.inflate(R.layout.timeline_list_header, linearLayout, false);
        linearLayout.addView(listHeader, 0);

        columnHeadersParent = listHeader.findViewById(R.id.columnHeadersParent);
        for (int i = 0; i < columnHeadersParent.getChildCount(); i++) {
            columnHeadersParent.getChildAt(i).setOnClickListener(v -> sortBy(v.getId()));
        }
    }

    @Override
    protected void onPause() {
        myContext.timelines().saveChanged();
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
                    MyLog.v("isDisplayedInSelector", () -> displayedInSelector.save() + " " +
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
    public void onLoadFinished(LoadableListPosition pos) {
        showSortColumn();
        super.onLoadFinished(pos);
    }

    private void showSortColumn() {
        for (int i = 0; i < columnHeadersParent.getChildCount(); i++) {
            View view = columnHeadersParent.getChildAt(i);
            if (!TextView.class.isAssignableFrom(view.getClass())) {
                continue;
            }
            TextView textView = (TextView) view;
            String text = textView.getText().toString();
            if (!StringUtils.isEmpty(text) && "▲▼↑↓".indexOf(text.charAt(0)) >= 0) {
                text = text.substring(1);
                textView.setText(text);
            }
            if (textView.getId() == sortByField) {
                textView.setText((sortDefault ? '▲' : '▼') + text);
            }
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
        return new SyncLoader<ManageTimelinesViewItem>() {
            @Override
            public void load(ProgressPublisher publisher) {
                items = myContext.timelines()
                        .stream()
                        .map(timeline -> new ManageTimelinesViewItem(myContext, timeline,
                                MyAccount.EMPTY, false))
                        .sorted(new ManageTimelinesViewItemComparator(sortByField, sortDefault, isTotal))
                        .collect(Collectors.toList());
                countersSince = items.stream().map(item -> item.countSince).filter(count -> count > 0)
                        .min(Long::compareTo).orElse(0L);
            }
        };
    }

    @Override
    protected BaseTimelineAdapter newListAdapter() {

        return new BaseTimelineAdapter<ManageTimelinesViewItem>(myContext,
                myContext.timelines().get(TimelineType.MANAGE_TIMELINES, Actor.EMPTY, Origin.EMPTY),
                getLoaded().getList()) {
            Timeline defaultTimeline = myContext.timelines().getDefault();

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView == null ? newView() : convertView;
                view.setOnCreateContextMenuListener(contextMenu);
                view.setOnClickListener(this);
                setPosition(view, position);
                final ManageTimelinesViewItem item = getItem(position);
                MyUrlSpan.showText(view, R.id.title, item.timelineTitle.toString(), false, true);
                MyUrlSpan.showText(view, R.id.account, item.timelineTitle.accountName, false, true);
                MyUrlSpan.showText(view, R.id.origin, item.timelineTitle.originName, false, true);
                showDisplayedInSelector(view, item);
                MyCheckBox.set(view, R.id.synced, item.timeline.isSyncedAutomatically(),
                        item.timeline.isSyncableAutomatically() ?
                                (CompoundButton.OnCheckedChangeListener) (buttonView, isChecked) -> {
                                    item.timeline.setSyncedAutomatically(isChecked);
                                    MyLog.v("isSyncedAutomatically", () ->
                                            (isChecked ? "+ " : "- ") + item.timeline);
                            } : null);
                MyUrlSpan.showText(view, R.id.syncedTimesCount,
                        I18n.notZero(item.timeline.getSyncedTimesCount(isTotal)), false, true);
                MyUrlSpan.showText(view, R.id.downloadedItemsCount,
                        I18n.notZero(item.timeline.getDownloadedItemsCount(isTotal)), false, true);
                MyUrlSpan.showText(view, R.id.newItemsCount,
                        I18n.notZero(item.timeline.getNewItemsCount(isTotal)), false, true);
                MyUrlSpan.showText(view, R.id.syncSucceededDate,
                        RelativeTime.getDifference(ManageTimelines.this, item.timeline.getSyncSucceededDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.syncFailedTimesCount,
                        I18n.notZero(item.timeline.getSyncFailedTimesCount(isTotal)), false, true);
                MyUrlSpan.showText(view, R.id.syncFailedDate,
                        RelativeTime.getDifference(ManageTimelines.this, item.timeline.getSyncFailedDate()),
                        false, true);
                MyUrlSpan.showText(view, R.id.errorMessage, item.timeline.getErrorMessage(), false, true);
                MyUrlSpan.showText(view, R.id.lastChangedDate,
                        RelativeTime.getDifference(ManageTimelines.this, item.timeline.getLastChangedDate()),
                        false, true);
                return view;
            }

            protected void showDisplayedInSelector(View parentView, final ManageTimelinesViewItem item) {
                CheckBox view = parentView.findViewById(R.id.displayedInSelector);
                MyCheckBox.set(parentView,
                        R.id.displayedInSelector,
                        item.timeline.isDisplayedInSelector() != DisplayedInSelector.NEVER,
                        (buttonView, isChecked) -> {
                            if (isChecked) {
                                selectedItem = item;
                                EnumSelector.newInstance(
                                        ActivityRequestCode.SELECT_DISPLAYED_IN_SELECTOR,
                                        DisplayedInSelector.class).show(ManageTimelines.this);
                            } else {
                                item.timeline.setDisplayedInSelector(DisplayedInSelector.NEVER);
                                buttonView.setText("");
                            }
                            MyLog.v("isDisplayedInSelector", () -> (isChecked ? "+ " : "- ") + item.timeline);
                        });
                view.setText(item.timeline.equals(defaultTimeline) ? "D" :
                        (item.timeline.isDisplayedInSelector() == DisplayedInSelector.ALWAYS ? "*" : ""));
            }

            private View newView() {
                return LayoutInflater.from(ManageTimelines.this).inflate(R.layout.timeline_list_item, null);
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.timeline_list, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected CharSequence getCustomTitle() {
        StringBuilder title = new StringBuilder(getTitle()
                + (getListAdapter().getCount() == 0 ? "" : " " + getListAdapter().getCount())
                + " / ");
        if (isTotal) {
            title.append(getText(R.string.total_counters));
        } else if (countersSince > 0) {
            title.append(StringUtils.format(this, R.string.since,
                    RelativeTime.getDifference(this, countersSince)));
        }
        return title;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.reset_counters_menu_item:
                myContext.timelines().resetCounters(isTotal);
                myContext.timelines().saveChanged();
                showList(WhichPage.CURRENT);
                break;
            case R.id.reset_timelines_order:
                myContext.timelines().resetDefaultSelectorOrder();
                sortBy(R.id.displayedInSelector);
                break;
            case R.id.total_counters:
                isTotal = !isTotal;
                item.setChecked(isTotal);
                showList(WhichPage.CURRENT);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenu != null) {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }

}
