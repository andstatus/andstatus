/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.MultiAutoCompleteTextView.Tokenizer
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import java.util.regex.Pattern

class NoteBodyTokenizer : Tokenizer {
    @Volatile
    private var origin: Origin? =  Origin.EMPTY
    fun setOrigin(origin: Origin?) {
        this.origin = origin
    }

    override fun findTokenStart(text: CharSequence?, cursor: Int): Int {
        var i = cursor
        while (i > 0) {
            if (nonWebfingerIdChar(text, i - 1)) {
                if (origin.isReferenceChar(text, i - 1)) {
                    i--
                }
                break
            }
            i--
        }
        if (i >= cursor - MIN_LENGHT_TO_SEARCH || !origin.isReferenceChar(text, i)) {
            return cursor
        }
        val start = i // Include reference char in the token
        MyLog.v(this) { "'$text', cursor=$cursor, start=$start" }
        return start
    }

    override fun findTokenEnd(text: CharSequence?, cursor: Int): Int {
        var i = cursor
        val length = text.length
        while (i < length) {
            if (nonWebfingerIdChar(text, i - 1)) {
                return i
            }
            i++
        }
        return length
    }

    override fun terminateToken(text: CharSequence?): CharSequence? {
        var i = text.length
        while (i > 0 && nonWebfingerIdChar(text, i)) {
            i--
        }
        if (i > 0 && nonWebfingerIdChar(text, i)) {
            return text
        } else if (text is Spanned) {
            val sp = SpannableString("$text ")
            TextUtils.copySpansFrom(text as Spanned?, 0, text.length,
                    Any::class.java, sp, 0)
            return sp
        }
        return text.toString() + " "
    }

    companion object {
        private val WEBFINGER_CHARACTERS_REGEX: String? = "[_A-Za-z0-9-+.@]+"
        const val MIN_LENGHT_TO_SEARCH = 2
        private val webFingerCharactersPattern = Pattern.compile(WEBFINGER_CHARACTERS_REGEX)
        private fun nonWebfingerIdChar(text: CharSequence?, cursor: Int): Boolean {
            return if (text == null || cursor < 0 || cursor >= text.length) {
                true
            } else !webFingerCharactersPattern.matcher(text.subSequence(cursor, cursor + 1)).matches()
        }
    }
}