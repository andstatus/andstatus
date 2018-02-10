/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.content.ContentValues;

/**
 * @author yvolk@yurivolkov.com
 */
public class AssertionData {
    private final String key;
    private final ContentValues values;

    public static AssertionData getEmpty(String keyIn) {
        return new AssertionData(keyIn, null);
    }
    
    public AssertionData(String keyIn, ContentValues valuesIn) {
        key = keyIn;
        if (valuesIn == null) {
            values = new ContentValues();
        } else {
            values = valuesIn;
        }
    }
    
    public String getKey() {
        return key;
    }

    public ContentValues getValues() {
        return values;
    }
    
    public boolean isEmpty() {
        return values.size() == 0;
    }
}
