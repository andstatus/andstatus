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

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class KeywordsFilter {
    private final List<String> keywordsToFilter;
    private final List<String> keywordsRaw;

    public KeywordsFilter(String keywordsIn) {
        keywordsToFilter = new ArrayList<>();
        keywordsRaw = new ArrayList<>();       
        if (TextUtils.isEmpty(keywordsIn)) {
            return;
        }
        for (String itemRaw : keywordsIn.split("[, ]")) {
            itemRaw = itemRaw.trim();
            String item = itemRaw.toLowerCase();
            if (!TextUtils.isEmpty(item) && !keywordsToFilter.contains(item)) {
                keywordsToFilter.add(item);
                keywordsRaw.add(itemRaw);
            }
        }
    }

    public boolean matched(String s) {
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
