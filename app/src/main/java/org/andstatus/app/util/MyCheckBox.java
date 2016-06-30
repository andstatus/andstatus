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

package org.andstatus.app.util;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.andstatus.app.R;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyCheckBox {
    private final static CompoundButton.OnCheckedChangeListener EMPTY_ON_CHECKED_CHANGE_LISTENER =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // Empty;
        }
    };

    private MyCheckBox() {
        // Empty
    }

    public static boolean isChecked(Activity activity, int checkBoxViewId, boolean defaultValue) {
        View view = activity.findViewById(checkBoxViewId);
        if (view == null || !CheckBox.class.isAssignableFrom(view.getClass())) {
            return defaultValue;
        }
        return ((CheckBox)view).isChecked();
    }

    public static void show(Activity activity, int viewId, boolean checked, boolean enabled) {
        show(activity, viewId, checked, enabled ? EMPTY_ON_CHECKED_CHANGE_LISTENER : null);
    }

    public static void show(Activity activity, int viewId, boolean checked,
                            CompoundButton.OnCheckedChangeListener onCheckedChangeListener) {
        show(activity.findViewById(R.id.my_layout_parent), viewId, checked, onCheckedChangeListener);
    }

    public static void show(View parentView, int viewId, boolean checked,
                            CompoundButton.OnCheckedChangeListener onCheckedChangeListener) {
        CheckBox checkBox = (CheckBox) parentView.findViewById(viewId);
        if (checkBox != null) {
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(checked);
            checkBox.setEnabled(onCheckedChangeListener != null);
            if (onCheckedChangeListener != null && onCheckedChangeListener != EMPTY_ON_CHECKED_CHANGE_LISTENER) {
                checkBox.setOnCheckedChangeListener(onCheckedChangeListener);
            }
        }
    }
}
