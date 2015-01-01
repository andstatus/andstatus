/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class AccountSelector extends ListActivity {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_CREDENTIALS_VERIFIED = "credentials_verified";
    private static final String KEY_SYNC_AUTO = "sync_auto";
    private static final String KEY_TYPE = "type";
    
    private static final String TYPE_ACCOUNT = "account";

    public static void selectAccount(Activity activity, long originId, ActivityRequestCode requestCode) {
        Intent intent = new Intent(activity, AccountSelector.class);
        intent.putExtra(IntentExtra.ORIGIN_ID.key, originId);
        activity.startActivityForResult(intent, requestCode.id);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Map<String, MyAccount> listData = newListData();
        if (listData.isEmpty()) {
            returnSelectedAccount(null);
            return;
        } else if (listData.size() == 1) {
            returnSelectedAccount(listData.entrySet().iterator().next().getValue());
            return;
        }
        
        setContentView(R.layout.accountlist);
        setListAdapter(newListAdapter(listData));

        getListView().setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long userId = Long.parseLong(((TextView)view.findViewById(R.id.id)).getText().toString());
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(userId);
                returnSelectedAccount(ma);
            }
        });
    }

    private Map<String, MyAccount> newListData() {
        long originId = getIntent().getLongExtra(IntentExtra.ORIGIN_ID.key, 0);
        SortedMap<String, MyAccount> listData = new TreeMap<String, MyAccount>();  
        for (MyAccount ma : MyContextHolder.get().persistentAccounts().collection()) {
            if (originId==0 || ma.getOriginId() == originId) {
                listData.put(ma.getAccountName(), ma);
            }
        }
        return listData;
    }

    private ListAdapter newListAdapter(Map<String, MyAccount> listData) {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (MyAccount ma : listData.values()) {
            Map<String, String> map = new HashMap<String, String>();
            String visibleName = ma.getAccountName();
            map.put(KEY_VISIBLE_NAME, visibleName);
            map.put(KEY_CREDENTIALS_VERIFIED,
                    ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED ? ""
                            : ma.getCredentialsVerified().name().substring(0, 1));
            map.put(KEY_SYNC_AUTO, ma.getSyncAutomatically() ? "" : getText(R.string.off).toString());
            map.put(BaseColumns._ID, Long.toString(ma.getUserId()));
            map.put(KEY_TYPE, TYPE_ACCOUNT);
            list.add(map);
        }
        
        return new MySimpleAdapter(this, 
                list, 
                R.layout.accountlist_item, 
                new String[] {KEY_VISIBLE_NAME, KEY_CREDENTIALS_VERIFIED, KEY_SYNC_AUTO, BaseColumns._ID, KEY_TYPE}, 
                new int[] {R.id.visible_name, R.id.credentials_verified, R.id.sync_auto, R.id.id, R.id.type});
    }

    private void returnSelectedAccount(MyAccount ma) {
        Intent dataToReturn = new Intent();
        if (ma != null) {
            dataToReturn.putExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key, ma.getAccountName());
        }
        setResult(RESULT_OK, dataToReturn);
        finish();
    }
}
