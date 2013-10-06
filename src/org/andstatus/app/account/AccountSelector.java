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

package org.andstatus.app.account;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.MyPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The purpose of this activity is to select Current account only, see {@link MyAccount#setCurrentMyAccount()}
 * @author yvolk@yurivolkov.com
 *
 */
public class AccountSelector extends ListActivity {
    private static final String TAG = AccountSelector.class.getSimpleName();

    private final String KEY_VISIBLE_NAME = "visible_name";
    private final String KEY_NAME = "name";
    private final String KEY_TYPE = "type";
    
    private final String TYPE_ACCOUNT = "account";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyPreferences.loadTheme(TAG, this);
        
        setContentView(R.layout.accountlist);
        
        long originId = getIntent().getLongExtra(IntentExtra.ORIGIN_ID.key, 0);
        SortedMap<String, MyAccount> accounts = new TreeMap<String, MyAccount>();  
        for (MyAccount ma : MyAccount.list()) {
            if (originId==0 || ma.getOriginId() == originId) {
                accounts.put(ma.getAccountName(), ma);
            }
        }
        ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
        for (MyAccount ma : accounts.values()) {
            HashMap<String, String> map = new HashMap<String, String>();
            String visibleName = ma.getAccountName();
            if (ma.getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                visibleName = "(" + visibleName + ")";
            }
            map.put(KEY_VISIBLE_NAME, visibleName);
            map.put(KEY_NAME, ma.getAccountName());
            map.put(KEY_TYPE, TYPE_ACCOUNT);
            data.add(map);
        }
        
        ListAdapter adapter = new SimpleAdapter(this, 
                data, 
                R.layout.accountlist_item, 
                new String[] {KEY_VISIBLE_NAME, KEY_NAME, KEY_TYPE}, 
                new int[] {R.id.visible_name, R.id.name, R.id.type});
        
        // Bind to our new adapter.
        setListAdapter(adapter);

        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String  accountName = ((TextView)view.findViewById(R.id.name)).getText().toString();
                MyAccount ma = MyAccount.fromAccountName(accountName);
                if (ma != null) {
                    Intent dataToReturn = new Intent();
                    dataToReturn.putExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key, ma.getAccountName());
                    AccountSelector.this.setResult(RESULT_OK, dataToReturn);
                    finish();
                }
            }
        });
    }
}
