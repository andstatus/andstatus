/* 
 * Copyright (c) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle;

public enum TriState {
    TRUE(2),
    FALSE(1),
    UNKNOWN(3);

    private final long id;
    
    TriState(long id) {
        this.id = id;
    }
    
    public static TriState fromId(long id) {
        for (TriState tt : TriState.values()) {
            if (tt.id == id) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    public static TriState fromBundle(Bundle bundle, String key) {
        return fromId(BundleUtils.fromBundle(bundle, key, UNKNOWN.id));
    }

    public Long getId() {
        return id;
    }
    
    public int getEntriesPosition() {
        return ordinal();
    }
    
    public static TriState fromEntriesPosition(int position) {
        TriState obj = UNKNOWN;
        for(TriState val : values()) {
            if (val.ordinal() == position) {
                obj = val;
                break;
            }
        }
        return obj;
    }
    
    @Override
    public String toString() {
        return "TriState:" + this.name();
    }
    
    public boolean toBoolean(boolean defaultValue) {
        switch (this){
            case FALSE:
                return false;
            case TRUE:
                return true;
            default:
                return defaultValue;
        }
    }

    public static TriState fromBoolean(boolean booleanToConvert) {
        if (booleanToConvert) {
            return TRUE;
        } else {
            return FALSE;
        }
    }

    public Bundle toBundle(String key) {
        return toBundle(new Bundle(), key);
    }

    public Bundle toBundle(Bundle bundle, String key) {
        bundle.putLong(key, id);
        return bundle;
    }
}
