/* 
 * Copyright (C) 2008 Torgny Bjers
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

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.MultiAutoCompleteTextView.Tokenizer;

/**
 * AtTokenizer allows for auto-completion of user names starting with an @ sign.
 * 
 * @author torgny.bjers
 */
public class AtTokenizer implements Tokenizer {
	/**
	 * @see android.widget.MultiAutoCompleteTextView.Tokenizer#findTokenStart(java.lang.CharSequence,
	 *      int)
	 */
	public int findTokenStart(CharSequence text, int cursor) {
		int i = cursor;
		while (i > 0 && text.charAt(i - 1) != '@')
			i--;
		while (i < cursor && text.charAt(i) == ' ')
			i++;
		return i;
	}

	/**
	 * @see android.widget.MultiAutoCompleteTextView.Tokenizer#findTokenEnd(java.lang.CharSequence,
	 *      int)
	 */
	public int findTokenEnd(CharSequence text, int cursor) {
		int i = cursor;
		int len = text.length();
		while (i < len) {
			if (text.charAt(i) == '@') {
				return i;
			} else {
				i++;
			}
		}
		return len;
	}

	/**
	 * @see android.widget.MultiAutoCompleteTextView.Tokenizer#terminateToken(java.lang.CharSequence)
	 */
	@Override
    public CharSequence terminateToken(CharSequence text) {
		int i = text.length();
		while (i > 0 && text.charAt(i - 1) == ' ')
			i--;
		if (i > 0 && text.charAt(i - 1) == '@') {
			return text;
		} else {
			if (text instanceof Spanned) {
				SpannableString sp = new SpannableString(text);
				TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, sp, 0);
				return sp;
			} else {
				return text;
			}
		}
	}
}