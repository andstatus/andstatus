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

package org.andstatus.app.msg;

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
import org.andstatus.app.origin.Origin;
import org.andstatus.app.widget.MySimpleAdapter;
import org.andstatus.app.context.MyContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineSelector extends SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_TYPE = "type";

    private static final String TYPE_TIMELINE = "timeline";

    public static void selectTimeline(FragmentActivity activity, ActivityRequestCode requestCode,
                                      Origin origin, MyAccount myAccount, boolean isCombined) {
        SelectorDialog selector = new TimelineSelector();
        selector.setRequestCode(requestCode).putLong(IntentExtra.ORIGIN_ID.key, origin.getId());
        selector.getArguments().putString(IntentExtra.ACCOUNT_NAME.key, myAccount.getAccountName());
        selector.getArguments().putBoolean(IntentExtra.TIMELINE_IS_COMBINED.key, isCombined);
        selector.show(activity);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(R.string.dialog_title_select_timeline);

        List<Timeline> listData = MyContextHolder.get().persistentTimelines().getFiltered(
                true,
                getArguments().getBoolean(IntentExtra.TIMELINE_IS_COMBINED.key),
                MyContextHolder.get().persistentAccounts().fromAccountName(
                        getArguments().getString(IntentExtra.ACCOUNT_NAME.key)),
                MyContextHolder.get().persistentOrigins().fromId(
                getArguments().getLong(IntentExtra.ORIGIN_ID.key, 0))
        );
        if (listData.isEmpty()) {
            returnSelectedTimeline(Timeline.getEmpty(null));
            return;
        } else if (listData.size() == 1) {
            returnSelectedTimeline(listData.get(0));
            return;
        }

        setListAdapter(newListAdapter(listData));

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long timelineId = Long.parseLong(((TextView) view.findViewById(R.id.id)).getText()
                        .toString());
                returnSelectedTimeline(MyContextHolder.get().persistentTimelines().fromId(timelineId));
            }
        });
    }

    private MySimpleAdapter newListAdapter(Collection<Timeline> listData) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Timeline timeline : listData) {
            Map<String, String> map = new HashMap<>();
            String visibleName = timeline.getName();
            map.put(KEY_VISIBLE_NAME, visibleName);
            map.put(BaseColumns._ID, Long.toString(timeline.getId()));
            map.put(KEY_TYPE, TYPE_TIMELINE);
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, BaseColumns._ID, KEY_TYPE},
                new int[] {R.id.visible_name, R.id.id, R.id.type}, true);
    }

    private void returnSelectedTimeline(Timeline timeline) {
        returnSelected(new Intent().putExtra(IntentExtra.TIMELINE_ID.key, timeline.getId()));
    }

}
