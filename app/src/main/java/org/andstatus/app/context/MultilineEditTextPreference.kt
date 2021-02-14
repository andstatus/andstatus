/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;

/**
 * See http://stackoverflow.com/questions/9220039/android-preferencescreen-title-in-two-lines
 */
public class MultilineEditTextPreference extends EditTextPreference {
    public MultilineEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MultilineEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MultilineEditTextPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder viewHolder) {
        super.onBindViewHolder(viewHolder);
        TextView textView = (TextView) viewHolder.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setSingleLine(false);
        }
    }
}
