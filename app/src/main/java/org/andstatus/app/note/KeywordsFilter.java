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

package org.andstatus.app.note;

import android.support.annotation.NonNull;

import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KeywordsFilter {
    static final String CONTAINS_PREFIX = "contains:";

    static class Keyword {
        final String value;
        final boolean contains;
        final boolean nonEmpty;

        Keyword(String value) {
            this(value, false);
        }

        Keyword(String value, boolean contains) {
            this.value = value;
            this.contains = contains;
            nonEmpty = StringUtils.nonEmpty(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Keyword keyword = (Keyword) o;
            return contains == keyword.contains && Objects.equals(value, keyword.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, contains);
        }

        @Override
        public String toString() {
            return "{" + (contains ? CONTAINS_PREFIX : "") + value + "}";
        }
    }

    final List<Keyword> keywordsToFilter;
    private final List<String> keywordsRaw;
    private static final char DOUBLE_QUOTE = '"';

    public KeywordsFilter(String keywordsIn) {
        keywordsRaw = parseFilterString(keywordsIn);
        keywordsToFilter = rawToActual(keywordsRaw);
    }

    @NonNull
    private List<String> parseFilterString(String text) {
        List<String> keywords = new ArrayList<>();
        if (StringUtils.isEmpty(text)) {
            return keywords;
        }
        boolean inQuote = false;
        for (int atPos = 0; atPos < text.length();) {
            int separatorInd = inQuote ? nextQuote(text, atPos) : nextSeparatorInd(text, atPos);
            if (atPos > separatorInd) {
                break;
            }
            String item = text.substring(atPos, separatorInd);
            if (!StringUtils.isEmpty(item) && !keywords.contains(item)) {
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
    private List<Keyword> rawToActual(List<String> keywordsRaw) {
        List<Keyword> keywords = new ArrayList<>();
        for (String itemRaw : keywordsRaw) {
            boolean contains = itemRaw.startsWith(CONTAINS_PREFIX);
            final String contentToSearch = MyHtml.getContentToSearch(contains
                            ? itemRaw.substring(CONTAINS_PREFIX.length())
                            : itemRaw);
            final String withContains = contains && contentToSearch.length() > 2
                    ? contentToSearch.substring(1, contentToSearch.length() - 1)
                    : contentToSearch;
            Keyword item = new Keyword(withContains, contains);
            if (item.nonEmpty && !keywords.contains(item)) {
                keywords.add(item);
            }
        }
        return keywords;
    }

    public boolean matchedAny(String s) {
        if (keywordsToFilter.isEmpty() || StringUtils.isEmpty(s)) {
            return false;
        }
        for (Keyword keyword : keywordsToFilter) {
            if (s.contains(keyword.value)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchedAll(String s) {
        if (keywordsToFilter.isEmpty() || StringUtils.isEmpty(s)) {
            return false;
        }
        for (Keyword keyword : keywordsToFilter) {
            if (!s.contains(keyword.value)) {
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
        StringBuilder selection = new StringBuilder();
        for (int ind = 0; ind < keywordsToFilter.size(); ind++) {
            if (ind > 0) {
                selection.append(" AND ");
            }
            selection.append(fieldName + " LIKE ?");
        }
        return selection.length() == 0 ? "" : "(" + selection.toString() + ")";
    }

    @NonNull
    public String[] prependSqlSelectionArgs(String[] selectionArgs) {
        String[] selectionArgsOut = selectionArgs;
        for (Keyword keyword : keywordsToFilter) {
            selectionArgsOut = StringUtils.addBeforeArray(selectionArgsOut, "%" + keyword.value + "%");
        }
        return selectionArgsOut;
    }

    @NonNull
    public String getFirstTagOrFirstKeyword() {
        for (String keyword : keywordsRaw) {
            if (keyword.startsWith("#")) {
                return keyword.substring(1);
            }
        }
        return keywordsRaw.isEmpty() ? "" : keywordsRaw.get(0);
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
