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

package org.andstatus.app.widget;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;

/**
 * @author yvolk@yurivolkov.com
 * Inspired by http://stackoverflow.com/questions/31832665/android-how-to-create-edit-text-with-single-line-input-and-multiline-hint
 */
public class LongHintEditText extends android.support.v7.widget.AppCompatEditText {
    boolean singleLine = false;
    public LongHintEditText(Context context) {
        super(context);
        setTextChangedListener();
    }

    private void setTextChangedListener() {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean singleLineNew = s.length() > 0;
                if (singleLine != singleLineNew) {
                    singleLine = singleLineNew;
                    LongHintEditText.this.setSingleLine(s.length() > 0);
                    if (singleLine) {
                        LongHintEditText.this.setSelection(s.length());
                    }
                }
            }
        });
    }

    public LongHintEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTextChangedListener();
    }

    public LongHintEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setTextChangedListener();
    }
}
