/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.lang;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class SelectableEnumList<E extends Enum<E> & SelectableEnum > {
    private List<E> list = new ArrayList<>();

    private SelectableEnumList(Class<E> clazz) {
        if (!SelectableEnum.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Class '" + clazz.getName() +
                    "' doesn't implement SelectableEnum");
        }
        for(E value : EnumSet.allOf(clazz)){
            if (value.isSelectable()) {
                list.add(value);
            }
        }
    }

    public static <E extends Enum<E> & SelectableEnum> SelectableEnumList<E> newInstance(Class<E> clazz) {
        return new SelectableEnumList<>(clazz);
    }

    public List<E> getList() {
        return list;
    }

    /**
     * @return -1 if the item is not found in the list
     */
    public int getIndex(SelectableEnum other) {
        for (int index = 0; index < list.size(); index++ ) {
            SelectableEnum selectableEnum =  list.get(index);
            if ( selectableEnum.equals(other)) {
                return index;
            }
        }
        return  -1;
    }

    /**
     * @return the first element if not found
     */
    public E get(int index) {
        return list.get(index >= 0 && index < list.size() ? index : 0);
    }

    public int getDialogTitleResId() {
        return list.get(0).getDialogTitleResId();
    }

    public ArrayAdapter<CharSequence> getSpinnerArrayAdapter(Context context) {
        ArrayAdapter<CharSequence> spinnerArrayAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, getTitles(context));
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return spinnerArrayAdapter;
    }

    private List<CharSequence> getTitles(Context context) {
        List<CharSequence> titles = new ArrayList<>();
        for (SelectableEnum selectableEnum : list) {
            titles.add(selectableEnum.title(context));
        }
        return titles;
    }

}
