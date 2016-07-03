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

import android.content.Intent;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import org.andstatus.app.widget.MySimpleAdapter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class EnumSelector<E extends Enum<E>> extends org.andstatus.app.SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private int dialogTitleResId;
    private EnumSet<E> enumSet;

    public static <E extends Enum<E>> SelectorDialog newInstance(ActivityRequestCode requestCode, Class<E> clazz) {
        if (!SelectableEnum.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class '" + clazz.getName() +
                    "' doesn't implement SelectableEnum");
        }
        EnumSelector selector = new EnumSelector();
        selector.setRequestCode(requestCode);
        selector.enumSet =  EnumSet.allOf(clazz);
        selector.dialogTitleResId = ((SelectableEnum) selector.enumSet.iterator().next()).getDialogTitleResId();
        return selector;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(dialogTitleResId);
        setListAdapter(newListAdapter());

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int idSelected = Integer.parseInt(((TextView) view.findViewById(R.id.id)).getText().toString());
                String code = "";
                for(E en : enumSet){
                    if (en.ordinal() == idSelected) {
                        code = ((SelectableEnum) en).getCode();
                    }
                }
                returnSelected(new Intent().putExtra(IntentExtra.SELECTABLE_ENUM.key, code));
            }
        });
    }

    private MySimpleAdapter newListAdapter() {
        List<Map<String, String>> list = new ArrayList<>();
        for(E en : enumSet){
            SelectableEnum value = (SelectableEnum) en;
            if (value.isSelectable()) {
                Map<String, String> map = new HashMap<>();
                map.put(KEY_VISIBLE_NAME, value.getTitle(getActivity()).toString());
                map.put(BaseColumns._ID, Integer.toString(en.ordinal()));
                list.add(map);
            }
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME, BaseColumns._ID},
                new int[] {R.id.visible_name, R.id.id}, true);
    }
}
