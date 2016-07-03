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
import android.provider.BaseColumns;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SelectorDialog;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.TriState;
import org.andstatus.app.widget.MySimpleAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineSelector extends SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_SYNC_AUTO = "sync_auto";

    public static void selectTimeline(FragmentActivity activity, ActivityRequestCode requestCode,
                                      Timeline timeline, MyAccount currentMyAccount) {
        SelectorDialog selector = new TimelineSelector();
        selector.setRequestCode(requestCode);
        selector.getArguments().putLong(IntentExtra.TIMELINE_ID.key, timeline.getId());
        selector.getArguments().putString(IntentExtra.ACCOUNT_NAME.key, currentMyAccount.getAccountName());
        selector.show(activity);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(R.string.dialog_title_select_timeline);
        Timeline timeline = myContext.persistentTimelines().fromId(getArguments().
                getLong(IntentExtra.TIMELINE_ID.key, 0));
        MyAccount currentMyAccount = myContext.persistentAccounts().fromAccountName(
                getArguments().getString(IntentExtra.ACCOUNT_NAME.key));

        List<Timeline> timelines = myContext.persistentTimelines().getFiltered(
                true,
                TriState.fromBoolean(timeline.isCombined()),
                currentMyAccount,
                timeline.getOrigin());
        if (timelines.isEmpty()) {
            returnSelectedTimeline(Timeline.getEmpty(null));
            return;
        } else if (timelines.size() == 1) {
            returnSelectedTimeline(timelines.get(0));
            return;
        }

        final List<TimelineListViewItem> items = new ArrayList<>();
        for (Timeline timeline2 : timelines) {
            TimelineListViewItem viewItem = new TimelineListViewItem(myContext, timeline2);
            items.add(viewItem);
        }
        Collections.sort(items, new TimelineListViewItemComparator(R.id.displayedInSelector, true));
        removeDuplicates(items);

        setListAdapter(newListAdapter(items));

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long timelineId = Long.parseLong(((TextView) view.findViewById(R.id.id)).getText()
                        .toString());
                returnSelectedTimeline(myContext.persistentTimelines().fromId(timelineId));
            }
        });
    }

    private void removeDuplicates(List<TimelineListViewItem> timelines) {
        Map<String, TimelineListViewItem> unique = new HashMap<>();
        boolean removeSomething = false;
        for (TimelineListViewItem viewItem : timelines) {
            String key = viewItem.timelineTitle.toString();
            if (unique.containsKey(key)) {
                removeSomething =  true;
            } else {
                unique.put(key, viewItem);
            }
        }
        if (removeSomething) {
            timelines.retainAll(unique.values());
        }
    }

    private MySimpleAdapter newListAdapter(List<TimelineListViewItem> listData) {
        List<Map<String, String>> list = new ArrayList<>();
        for (TimelineListViewItem viewItem : listData) {
            Map<String, String> map = new HashMap<>();
            map.put(KEY_VISIBLE_NAME, viewItem.timelineTitle.toString());
            map.put(KEY_SYNC_AUTO, viewItem.timeline.isSyncedAutomatically() ? "X" : "");
            map.put(BaseColumns._ID, Long.toString(viewItem.timeline.getId()));
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID},
                new int[] {R.id.visible_name, R.id.sync_auto, R.id.id}, true);
    }

    private void returnSelectedTimeline(Timeline timeline) {
        returnSelected(new Intent().putExtra(IntentExtra.TIMELINE_ID.key, timeline.getId()));
    }

}
