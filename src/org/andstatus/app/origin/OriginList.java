/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Select or Manage Origins
 * @author yvolk@yurivolkov.com
 */
public class OriginList extends ListActivity {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_NAME = "name";
    
    private final List<Map<String, String>> data = new ArrayList<Map<String, String>>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.origin_list);

        ListAdapter adapter = new SimpleAdapter(this, 
                data, 
                R.layout.origin_list_item, 
                new String[] {KEY_VISIBLE_NAME, KEY_NAME}, 
                new int[] {R.id.visible_name, R.id.name});
        
        // Bind to our new adapter.
        setListAdapter(adapter);

        processNewIntent(getIntent());
    }

    /**
     * Change the Activity according to the new intent. This procedure is done
     * both {@link #onCreate(Bundle)} and {@link #onNewIntent(Intent)}
     * 
     * @param intentNew
     */
    private void processNewIntent(Intent intentNew) {
        String action = intentNew.getAction();
        boolean actionPick = action!=null && action.equals(Intent.ACTION_PICK);
        Button buttonAdd = (Button) findViewById(R.id.button_add);
        if (actionPick) {
            getListView().setOnItemClickListener(new Picker());
            buttonAdd.setVisibility(android.view.View.GONE);
        } else {
            getListView().setOnItemClickListener(new Updater());
            buttonAdd.setVisibility(android.view.View.VISIBLE);
            buttonAdd.setOnClickListener(new AddClickListener());
        }
        fillList(actionPick);
    }

    private void fillList(boolean actionPick) {
        data.clear();
        for (Origin origin : MyContextHolder.get().persistentOrigins().collection()) {
            if (actionPick || android.os.Build.VERSION.SDK_INT >= OriginType.TWITTER_ACCOUNT_ADDING_MIN
                    || origin.originType != OriginType.TWITTER) {
                Map<String, String> map = new HashMap<String, String>();
                String visibleName = origin.getName();
                map.put(KEY_VISIBLE_NAME, visibleName);
                map.put(KEY_NAME, origin.getName());
                data.add(map);
            }
        }
        MyLog.v(this, "fillList, " + data.size() + " items");
        ((SimpleAdapter) getListAdapter()).notifyDataSetChanged(); 
    }

    private class Picker implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String name = ((TextView)view.findViewById(R.id.name)).getText().toString();
            Origin origin = MyContextHolder.get().persistentOrigins().fromName(name);
            if (origin.isPersistent()) {
                Intent dataToReturn = new Intent();
                dataToReturn.putExtra(IntentExtra.EXTRA_ORIGIN_NAME.key, origin.getName());
                OriginList.this.setResult(RESULT_OK, dataToReturn);
                finish();
            }
        }
    }

    private class Updater implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String name = ((TextView)view.findViewById(R.id.name)).getText().toString();
            Origin origin = MyContextHolder.get().persistentOrigins().fromName(name);
            if (origin.isPersistent()) {
                Intent intent = new Intent(OriginList.this, OriginEditor.class);
                intent.setAction(Intent.ACTION_EDIT);
                intent.putExtra(IntentExtra.EXTRA_ORIGIN_NAME.key, origin.getName());
                startActivityForResult(intent, ActivityRequestCode.EDIT_ORIGIN.id);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        MyLog.v(this, "onActivityResult " + ActivityRequestCode.fromId(requestCode) );
        switch (ActivityRequestCode.fromId(requestCode)) {
            case EDIT_ORIGIN:
                fillList(false);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private class AddClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(OriginList.this, OriginEditor.class);
            intent.setAction(Intent.ACTION_INSERT);
            startActivityForResult(intent, ActivityRequestCode.EDIT_ORIGIN.id);
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processNewIntent(intent);
    }
}
