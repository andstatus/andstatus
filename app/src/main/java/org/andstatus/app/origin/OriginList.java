/* 
 * Copyright (c) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import android.content.Intent;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.list.MyListActivity;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.MySimpleAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Select or Manage Origins
 * @author yvolk@yurivolkov.com
 */
public abstract class OriginList extends MyListActivity {
    protected static final String KEY_VISIBLE_NAME = "visible_name";
    protected static final String KEY_NAME = "name";
    
    private final List<Map<String, String>> data = new ArrayList<>();
    protected boolean addEnabled = false;
    protected OriginType originType = OriginType.UNKNOWN;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = getLayoutResourceId();
        super.onCreate(savedInstanceState);
        processNewIntent(getIntent());
    }

    protected int getLayoutResourceId() {
        return R.layout.my_list;
    }

    /**
     * Change the Activity according to the new intent. This procedure is done
     * both {@link #onCreate(Bundle)} and {@link #onNewIntent(Intent)}
     */
    private void processNewIntent(Intent intentNew) {
        String action = intentNew.getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_INSERT.equals(action)) {
            getListView().setOnItemClickListener(new Picker());
        } else {
            getListView().setOnItemClickListener(new Updater());
        }
        addEnabled = !Intent.ACTION_PICK.equals(action);
        originType = OriginType.fromCode(intentNew.getStringExtra(IntentExtra.ORIGIN_TYPE.key));
        if (Intent.ACTION_INSERT.equals(action)) {
            getSupportActionBar().setTitle(R.string.select_social_network);
        }

        ListAdapter adapter = new MySimpleAdapter(this,
                data,
                R.layout.origin_list_item,
                new String[] {KEY_VISIBLE_NAME, KEY_NAME},
                new int[] {R.id.visible_name, R.id.name}, true);
        // Bind to our new adapter.
        setListAdapter(adapter);

        fillList();
    }

    protected void fillList() {
        data.clear();
        fillData(data);
        data.sort((lhs, rhs) -> lhs.get(KEY_VISIBLE_NAME).compareToIgnoreCase(rhs.get(KEY_VISIBLE_NAME)));
        MyLog.v(this, "fillList, " + data.size() + " items");
        ((BaseAdapter) getListAdapter()).notifyDataSetChanged();
    }

    protected final void fillData(List<Map<String, String>> data) {
        for (Origin origin : getOrigins()) {
            if (originType.equals(OriginType.UNKNOWN) || originType.equals(origin.getOriginType())) {
                Map<String, String> map = new HashMap<>();
                String visibleName = origin.getName();
                map.put(KEY_VISIBLE_NAME, visibleName);
                map.put(KEY_NAME, origin.getName());
                map.put(BaseColumns._ID, Long.toString(origin.getId()));
                data.add(map);
            }
        }
    }

    protected abstract Iterable<Origin> getOrigins();

    private class Picker implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String name = ((TextView)view.findViewById(R.id.name)).getText().toString();
            Intent dataToReturn = new Intent();
            dataToReturn.putExtra(IntentExtra.ORIGIN_NAME.key, name);
            OriginList.this.setResult(RESULT_OK, dataToReturn);
            finish();
        }
    }

    private class Updater implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String name = ((TextView)view.findViewById(R.id.name)).getText().toString();
            Origin origin = MyContextHolder.get().origins().fromName(name);
            if (origin.isPersistent()) {
                Intent intent = new Intent(OriginList.this, OriginEditor.class);
                intent.setAction(Intent.ACTION_EDIT);
                intent.putExtra(IntentExtra.ORIGIN_NAME.key, origin.getName());
                startActivityForResult(intent, ActivityRequestCode.EDIT_ORIGIN.id);
            }
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processNewIntent(intent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(getMenuResourceId(), menu);
        return super.onCreateOptionsMenu(menu);
    }

    protected abstract int getMenuResourceId();
    
}
