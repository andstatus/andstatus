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

package org.andstatus.app.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.lang.SelectableEnumList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yvolk@yurivolkov.com
 */
public class EnumSelector<E extends Enum<E> & SelectableEnum> extends SelectorDialog {
    private static final String KEY_VISIBLE_NAME = "visible_name";
    private SelectableEnumList<E> enumList = null;

    public static <E extends Enum<E> & SelectableEnum> SelectorDialog newInstance(
            ActivityRequestCode requestCode, Class<E> clazz) {
        EnumSelector selector = new EnumSelector();
        selector.setRequestCode(requestCode);
        selector.enumList = SelectableEnumList.newInstance(clazz);
        return selector;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (enumList == null || enumList.getDialogTitleResId() == 0) {  // We don't save a state of the dialog
            dismiss();
            return;
        }
        setTitle(enumList.getDialogTitleResId());
        setListAdapter(newListAdapter());

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                returnSelected(new Intent().putExtra(IntentExtra.SELECTABLE_ENUM.key, enumList.get(position).getCode()));
            }
        });
    }

    private MySimpleAdapter newListAdapter() {
        List<Map<String, String>> list = new ArrayList<>();
        for(SelectableEnum value : enumList.getList()){
            Map<String, String> map = new HashMap<>();
            map.put(KEY_VISIBLE_NAME, value.getTitle(getActivity()).toString());
            list.add(map);
        }

        return new MySimpleAdapter(getActivity(),
                list,
                R.layout.accountlist_item,
                new String[] {KEY_VISIBLE_NAME},
                new int[] {R.id.visible_name}, true);
    }
}
