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
package org.andstatus.app.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * @author yvolk@yurivolkov.com
 * Inspired by http://stackoverflow.com/questions/31832665/android-how-to-create-edit-text-with-single-line-input-and-multiline-hint
 */
class LongHintEditText : AppCompatEditText {
    var singleLine = false

    constructor(context: Context?) : super(context) {
        setTextChangedListener()
    }

    private fun setTextChangedListener() {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val singleLineNew = s.length > 0
                if (singleLine != singleLineNew) {
                    singleLine = singleLineNew
                    this@LongHintEditText.isSingleLine = s.length > 0
                    if (singleLine) {
                        this@LongHintEditText.setSelection(s.length)
                    }
                }
            }
        })
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        setTextChangedListener()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setTextChangedListener()
    }
}