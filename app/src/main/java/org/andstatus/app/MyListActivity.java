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

package org.andstatus.app;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Support library doesn't have ListActivity, so we recreated it using one of two options:
 * 1. Fragments
 * 2. ListView
 * And now it looks like we don't need ListFragment at all!
 * @author yvolk@yurivolkov.com
 */
public class MyListActivity extends MyBaseListActivity {
    ListFragment listFragment = null;
    ListView listView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void setListAdapter(ListAdapter adapter) {
        findListView();
        if (listFragment != null) {
            listFragment.setListAdapter(adapter);
        } else if (listView != null) {
            listView.setAdapter(adapter);
        }
    }

    @Override
    public ListAdapter getListAdapter() {
        findListView();
        if (listFragment != null) {
            return listFragment.getListAdapter();
        } else if (listView != null) {
            return listView.getAdapter();
        }
        return null;
    }

    @Override
    public ListView getListView() {
        findListView();
        if (listFragment != null) {
            return listFragment.getListView();
        }
        return listView;
    }

    private void findListView() {
        if (listFragment == null && listView == null) {
            listFragment = (ListFragment) getSupportFragmentManager().findFragmentById(R.id.relative_list_parent);
            if (listFragment == null) {
                listView = (ListView) findViewById(android.R.id.list);
            }
        }
    }
}
