/* 
 * Copyright (c) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.IntentExtra;

/** @author yvolk@yurivolkov.com */
public enum TriState {
    TRUE(2, true, false),
    FALSE(1, false, true),
    UNKNOWN(3, false, false);

    public final long id;
    public final boolean isTrue;
    /** https://philosophy.stackexchange.com/questions/38542/what-is-the-difference-if-any-between-not-true-and-false */
    public final boolean untrue;
    public final boolean isFalse;
    public final boolean notFalse;
    public final boolean known;
    public final boolean unknown;

    TriState(long id, boolean isTrue, boolean isFalse) {
        this.id = id;
        this.isTrue = isTrue;
        untrue = !isTrue;
        this.isFalse = isFalse;
        notFalse = !isFalse;
        known = isTrue || isFalse;
        unknown = !known;
    }
    
    public static TriState fromId(long id) {
        for (TriState tt : TriState.values()) {
            if (tt.id == id) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    public static TriState fromBundle(Bundle bundle, IntentExtra intentExtra) {
        return fromId(BundleUtils.fromBundle(bundle, intentExtra, UNKNOWN.id));
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

    public Bundle toBundle(Bundle bundle, String key) {
        bundle.putLong(key, id);
        return bundle;
    }

    public <T> T select(T ifTrue, T ifFalse, T ifUnknown) {
        switch (this) {
            case TRUE:
                return ifTrue;
            case FALSE:
                return ifFalse;
            default:
                return ifUnknown;
        }
    }
}
