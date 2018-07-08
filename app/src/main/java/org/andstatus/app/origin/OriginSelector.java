/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.view.MyContextMenu;
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
public class OriginSelector extends SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private static final String KEY_SYNC_AUTO = "sync_auto";

    public static void selectOriginForActor(FragmentActivity activity, int menuGroup,
                                            ActivityRequestCode requestCode, Actor actor) {
        SelectorDialog selector = new OriginSelector();
        selector.setRequestCode(requestCode).putLong(IntentExtra.ACTOR_ID.key, actor.actorId);
        selector.myGetArguments().putInt(IntentExtra.MENU_GROUP.key, menuGroup);
        selector.show(activity);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(R.string.select_social_network);

        List<Origin> listData = newListData();
        if (listData.isEmpty()) {
            returnSelectedItem(Origin.EMPTY);
            return;
        } else if (listData.size() == 1) {
            returnSelectedItem(listData.get(0));
            return;
        }

        setListAdapter(newListAdapter(listData));

        getListView().setOnItemClickListener((parent, view, position, id) -> {
            long selectedId = Long.parseLong(((TextView) view.findViewById(R.id.id)).getText().toString());
            returnSelectedItem(MyContextHolder.get().origins().fromId(selectedId));
        });
    }

    private List<Origin> newListData() {
        return getOriginsForActor();
    }

    private List<Origin> getOriginsForActor() {
        final Long actorId = Optional.ofNullable(getArguments())
                .map(bundle -> bundle.getLong(IntentExtra.ACTOR_ID.key)).orElse(0L);
        return Actor.load(MyContextHolder.get(), actorId).user.knownInOrigins(MyContextHolder.get());
    }

    private MySimpleAdapter newListAdapter(List<Origin> listData) {
        List<Map<String, String>> list = new ArrayList<>();
        for (Origin item : listData) {
            Map<String, String> map = new HashMap<>();
            String visibleName = item.name;
            if (!item.isValid()) {
                visibleName = "(" + visibleName + ")";
            }
            map.put(KEY_VISIBLE_NAME, visibleName);
            map.put(BaseColumns._ID, Long.toString(item.id));
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, KEY_SYNC_AUTO, BaseColumns._ID},
                new int[] {R.id.visible_name, R.id.sync_auto, R.id.id}, true);
    }

    private void returnSelectedItem(@NonNull Origin item) {
        returnSelected(new Intent()
                .putExtra(IntentExtra.ORIGIN_NAME.key, item.name)
                .putExtra(IntentExtra.MENU_GROUP.key,
                        myGetArguments().getInt(IntentExtra.MENU_GROUP.key, MyContextMenu.MENU_GROUP_NOTE))
        );
    }

}
