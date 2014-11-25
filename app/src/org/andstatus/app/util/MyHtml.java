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

package org.andstatus.app.util;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;

public class MyHtml {
    private MyHtml() {
        // Empty
    }

    public static String htmLify(String messageIn) {
        return messageIn.replaceAll("(\r\n|\n)", "<br />");
    }

    private static String NEWLINE_SEARCH = "\n";
    private static String NEWLINE_REPLACE = "\n";
    public static String fromHtml(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        } else if ( MyHtml.hasHtmlMarkup(text)) {
            String text2 = text.trim();
            text2 = Html.fromHtml(text2).toString().trim();
            text2 = text2.replaceAll(NEWLINE_SEARCH + "\\s*" + NEWLINE_SEARCH, NEWLINE_REPLACE);
            if (text2.endsWith(NEWLINE_REPLACE)) {
                text2 = text2.substring(0, text2.length() - NEWLINE_REPLACE.length());
            }
            return text2;
        } else {
            return text.trim();
        }
    }

    /** Very simple method  
     */
    public static boolean hasHtmlMarkup(String text) {
        boolean has = false;
        if (text != null){
            has = text.contains("<") && text.contains(">");
        }
        return has; 
    }

    public static boolean hasUrlSpans (Spanned spanned) {
        boolean has = false;
        if (spanned != null){
            URLSpan[] spans = spanned.getSpans(0, spanned.length(), URLSpan.class);
            has = spans != null && spans.length > 0;
        }
        return has; 
    }
}
