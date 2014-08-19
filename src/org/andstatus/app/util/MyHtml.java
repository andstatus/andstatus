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

    private static String NEWLINE_TEMP = "newline_temp_subst";
    public static String stripHtml(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        } else if ( MyHtml.hasHtmlMarkup(text)) {
            String text2 = text.trim();
            text2 = text2.replaceAll("<br\\s?[/]?>", NEWLINE_TEMP);
            text2 = text2.replaceAll("</p>", "\1" + NEWLINE_TEMP);
            text2 = text2.replaceAll(NEWLINE_TEMP + "\\s*" + NEWLINE_TEMP, NEWLINE_TEMP);
            if (text2.endsWith(NEWLINE_TEMP)) {
                text2 = text2.substring(0, text2.length() - NEWLINE_TEMP.length());
            }
            text2 = Html.fromHtml(Html.fromHtml(text2.trim()).toString()).toString().trim();
            return text2.replaceAll(NEWLINE_TEMP, "\n");
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
