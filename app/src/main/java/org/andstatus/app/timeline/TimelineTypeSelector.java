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
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SelectorDialog;
import org.andstatus.app.widget.MySimpleAdapter;
import org.andstatus.app.context.MyPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineTypeSelector extends org.andstatus.app.SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";

    static SelectorDialog newInstance(ActivityRequestCode requestCode) {
        SelectorDialog selector = new TimelineTypeSelector();
        selector.setRequestCode(requestCode);
        return selector;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(R.string.dialog_title_select_timeline);
        setListAdapter(newListAdapter());

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int userId = Integer.parseInt(((TextView) view.findViewById(R.id.id)).getText()
                        .toString());
                returnSelectedAccount(TimelineType.values()[userId]);
            }
        });
    }

    private MySimpleAdapter newListAdapter() {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (TimelineType timelineType : TimelineType.defaultMyAccountTimelineTypes) {
            Map<String, String> map = new HashMap<String, String>();
            map.put(KEY_VISIBLE_NAME, timelineType.getTitle(getActivity()).toString());
            map.put(BaseColumns._ID, Long.toString(timelineType.ordinal()));
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, BaseColumns._ID},
                new int[] {R.id.visible_name, R.id.id}, true);
    }

    private void returnSelectedAccount(TimelineType timelineType) {
        returnSelected(new Intent().putExtra(IntentExtra.TIMELINE_TYPE.key, timelineType.save()));
    }

}
