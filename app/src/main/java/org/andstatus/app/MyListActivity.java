/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
 * Support library doesn't have ListActivity, so we recreated it using Fragments here
 * It assumes the list fragment has this id: R.id.myLayoutParent
 * @author yvolk@yurivolkov.com
 */
public class MyListActivity extends MyBaseListActivity {
    ListFragment mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void setListAdapter(ListAdapter adapter) {
        getList().setListAdapter(adapter);
    }

    @Override
    public ListView getListView() {
        return getList().getListView();
    }

    @Override
    public ListAdapter getListAdapter() {
        if (getList() != null) {
            return getList().getListAdapter();
        }
        return null;
    }

    private ListFragment getList() {
        if (mList == null) {
            mList = (ListFragment) getSupportFragmentManager().findFragmentById(R.id.myListParent);
        }
        return mList;
    }

}
