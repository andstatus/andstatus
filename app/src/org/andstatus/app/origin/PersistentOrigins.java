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

package org.andstatus.app.origin;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.util.MyLog;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentOrigins {
    private final Map<String,Origin> mOrigins = new ConcurrentHashMap<String, Origin>();
    
    private PersistentOrigins() {
    }

    public PersistentOrigins initialize() {
        return initialize(MyContextHolder.get());
    }
    
    public PersistentOrigins initialize(MyContext myContext) {
        return initialize(myContext.getDatabase().getWritableDatabase());
    }
    
    public PersistentOrigins initialize(SQLiteDatabase db) {
        String sql = "SELECT * FROM " + MyDatabase.Origin.TABLE_NAME;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            mOrigins.clear();
            while (cursor.moveToNext()) {
                Origin origin = new Origin.Builder(cursor).build();
                mOrigins.put(origin.name, origin);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        
        MyLog.v(this, "Initialized " + mOrigins.size() + " origins");
        return this;
    }
    
    public static PersistentOrigins getEmpty() {
        return new PersistentOrigins();
    }

    /**
     * @return Origin of UNKNOWN type if not found
     */
    public Origin fromId(long originId) {
        for (Origin origin : mOrigins.values()) {
            if (origin.id == originId) {
                return origin;
            }
        }
        return Origin.Builder.getUnknown();
    }
    
    /**
     * @return Origin of UNKNOWN type if not found
     */
    public Origin fromName(String originName) {
        Origin origin = null;
        if (!TextUtils.isEmpty(originName)) {
            origin = mOrigins.get(originName);
        }
        if (origin == null) {
            origin = Origin.Builder.getUnknown();
        }
        return origin;
    }

    /**
     * @return Origin of this type or empty Origin of UNKNOWN type if not found
     */
    public Origin firstOfType(OriginType originType) {
        for (Origin origin : mOrigins.values()) {
            if (origin.originType == originType) {
                return origin;
            }
        }
        return Origin.Builder.getUnknown();
    }

    public Collection<Origin> collection() {
        return mOrigins.values();
    }
    
    public boolean isHtmlContentAllowed(long originId) {
        return fromId(originId).isHtmlContentAllowed();
    }
}
