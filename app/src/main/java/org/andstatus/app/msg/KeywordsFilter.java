/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class KeywordsFilter {
    final List<String> keywordsToFilter;
    private final List<String> keywordsRaw;
    private static final char DOUBLE_QUOTE = '"';

    public KeywordsFilter(String keywordsIn) {
        keywordsRaw = parseFilterString(keywordsIn);
        keywordsToFilter = rawToActual(keywordsRaw);
    }

    @NonNull
    private List<String> parseFilterString(String text) {
        List<String> keywords = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return keywords;
        }
        boolean inQuote = false;
        for (int atPos = 0; atPos < text.length();) {
            int separatorInd = inQuote ? nextQuote(text, atPos) : nextSeparatorInd(text, atPos);
            if (atPos > separatorInd) {
                break;
            }
            String item = text.substring(atPos, separatorInd);
            if (!TextUtils.isEmpty(item) && !keywords.contains(item)) {
                keywords.add(item);
            }
            if (separatorInd < text.length() && text.charAt(separatorInd) == '"') {
                inQuote = !inQuote;
            }
            atPos = separatorInd + 1;
        }
        return keywords;
    }

    private int nextQuote(String text, int atPos) {
        for (int ind=atPos; ind < text.length(); ind++) {
            if (DOUBLE_QUOTE == text.charAt(ind)) {
                return ind;
            }
        }
        return text.length();
    }

    private int nextSeparatorInd(String text, int atPos) {
        final String SEPARATORS = ", " + DOUBLE_QUOTE;
        for (int ind=atPos; ind < text.length(); ind++) {
            if (SEPARATORS.indexOf(text.charAt(ind)) >= 0) {
                return ind;
            }
        }
        return text.length();
    }

    @NonNull
    private List<String> rawToActual(List<String> keywordsRaw) {
        List<String> keywords = new ArrayList<>();
        for (String itemRaw : keywordsRaw) {
            String item = MyHtml.getBodyToSearch(itemRaw);
            if (!TextUtils.isEmpty(item) && !keywords.contains(item)) {
                keywords.add(item);
            }
        }
        return keywords;
    }

    public boolean matchedAny(String s) {
        if (keywordsToFilter.isEmpty() || TextUtils.isEmpty(s)) {
            return false;
        }
        for (String keyword : keywordsToFilter) {
            if (s.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchedAll(String s) {
        if (keywordsToFilter.isEmpty() || TextUtils.isEmpty(s)) {
            return false;
        }
        for (String keyword : keywordsToFilter) {
            if (!s.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    public String getSqlSelection(String fieldName) {
        if (isEmpty()) {
            return "";
        }
        String selection = "";
        for (int ind=0; ind<keywordsToFilter.size(); ind++) {
            if (ind > 0) {
                selection += " AND ";
            }
            selection += fieldName + " LIKE ?";
        }
        return "(" + selection + ")";
    }

    @NonNull
    public String[] prependSqlSelectionArgs(String[] selectionArgs) {
        String[] selectionArgsOut = selectionArgs;
        for (String keyword : keywordsToFilter) {
            selectionArgsOut = StringUtils.addBeforeArray(selectionArgsOut, "%" + keyword + "%");
        }
        return selectionArgsOut;
    }

    public boolean isEmpty() {
        return keywordsToFilter.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (String keyword : keywordsRaw) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("\"" + keyword + "\"");
        }
        return builder.toString();
    }
}
