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

package org.andstatus.app.msg;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView;

import org.andstatus.app.util.MyLog;

import java.util.regex.Pattern;

class MessageBodyTokenizer implements MultiAutoCompleteTextView.Tokenizer {
    private static final String WEBFINGER_CHARACTERS_REGEX = "[_A-Za-z0-9-\\+\\.@]+";
    private static final int MIN_LENGHT_TO_SEARCH = 2;
    private static Pattern webFingerCharactersPattern = Pattern.compile(WEBFINGER_CHARACTERS_REGEX);

    @Override
    public int findTokenStart(final CharSequence text, final int cursor) {
        int i = cursor;
        while (i > 0) {
            if (!isWebfingerIdChar(text, i - 1)) {
                break;
            }
            i--;
        }
        if (i >= cursor - MIN_LENGHT_TO_SEARCH || text == null || text.charAt(i) != '@') {
            return cursor;
        }
        MyLog.v(this, "'" + text + "', cursor=" + cursor + ", start=" + (i + 1));
        return i + 1; // Skip the "@"
    }

    @Override
    public int findTokenEnd(CharSequence text, int cursor) {
        int i = cursor;
        int length = text.length();
        while (i < length) {
            if (!isWebfingerIdChar(text, i - 1)) {
                return i;
            }
            i++;
        }
        return length;
    }

    @Override
    public CharSequence terminateToken(CharSequence text) {
        int i = text.length();

        while (i > 0 && !isWebfingerIdChar(text, i)) {
            i--;
        }

        if (i > 0 && !isWebfingerIdChar(text, i)) {
            return text;
        } else if (text instanceof Spanned) {
            SpannableString sp = new SpannableString(text + " ");
            TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
                    Object.class, sp, 0);
            return sp;
        }
        return text + " ";
    }

    private static boolean isWebfingerIdChar(CharSequence text, int cursor) {
        if (text == null || cursor < 0 || cursor >= text.length()) {
            return false;
        }
        return webFingerCharactersPattern.matcher(text.subSequence(cursor, cursor+1)).matches();
    }
}
