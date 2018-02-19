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

package org.andstatus.app.account;

import android.content.Intent;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.view.MySimpleAdapter;
import org.andstatus.app.view.SelectorDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountSelector extends SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_SYNC_AUTO = "sync_auto";

    public static void selectAccount(FragmentActivity activity, ActivityRequestCode requestCode, long originId) {
        SelectorDialog selector = new AccountSelector();
        selector.setRequestCode(requestCode).putLong(IntentExtra.ORIGIN_ID.key, originId);
        selector.show(activity);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(R.string.label_accountselector);

        List<MyAccount> listData = newListData();
        if (listData.isEmpty()) {
            returnSelectedAccount(MyAccount.EMPTY);
            return;
        } else if (listData.size() == 1) {
            returnSelectedAccount(listData.get(0));
            return;
        }

        setListAdapter(newListAdapter(listData));

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long actorId = Long.parseLong(((TextView) view.findViewById(R.id.id)).getText()
                        .toString());
                returnSelectedAccount(MyContextHolder.get().accounts().fromActorId(actorId));
            }
        });
    }

    private List<MyAccount> newListData() {
        long originId = Optional.ofNullable(getArguments())
                .map(bundle -> bundle.getLong(IntentExtra.ORIGIN_ID.key)).orElse(0L);
        List<MyAccount> listData = new ArrayList<>();
        for (MyAccount ma : MyContextHolder.get().accounts().get()) {
            if (originId==0 || ma.getOriginId() == originId) {
                listData.add(ma);
            }
        }
        return listData;
    }

    private MySimpleAdapter newListAdapter(List<MyAccount> listData) {
        List<Map<String, String>> list = new ArrayList<>();
        final String syncText = getText(R.string.synced_abbreviated).toString();
        for (MyAccount ma : listData) {
            Map<String, String> map = new HashMap<>();
            String visibleName = ma.getAccountName();
            if (!ma.isValidAndSucceeded()) {
                visibleName = "(" + visibleName + ")";
            }
            map.put(KEY_VISIBLE_NAME, visibleName);
            map.put(KEY_SYNC_AUTO, ma.isSyncedAutomatically() && ma.isValidAndSucceeded() ? syncText : "");
            map.put(BaseColumns._ID, Long.toString(ma.getActorId()));
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID},
                new int[] {R.id.visible_name, R.id.sync_auto, R.id.id}, true);
    }

    private void returnSelectedAccount(@NonNull MyAccount ma) {
        returnSelected(new Intent().putExtra(IntentExtra.ACCOUNT_NAME.key, ma.getAccountName()));
    }

}
