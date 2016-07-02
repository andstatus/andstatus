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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextImpl;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.OriginTable;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentOrigins {
    private final MyContext myContext;
    private final Map<String,Origin> mOrigins = new ConcurrentHashMap<String, Origin>();
    
    private PersistentOrigins(MyContextImpl myContext) {
        this.myContext = myContext;
    }

    public PersistentOrigins initialize() {
        return initialize(myContext.getDatabase());
    }
    
    public PersistentOrigins initialize(SQLiteDatabase db) {
        String sql = "SELECT * FROM " + OriginTable.TABLE_NAME;
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
    
    public static PersistentOrigins newEmpty(MyContextImpl myContext) {
        return new PersistentOrigins(myContext);
    }

    /**
     * @return Origin of UNKNOWN type if not found
     */
    @NonNull
    public Origin fromId(long originId) {
        for (Origin origin : mOrigins.values()) {
            if (origin.id == originId) {
                return origin;
            }
        }
        return Origin.getEmpty();
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
            origin = Origin.getEmpty();
        }
        return origin;
    }

    /**
     * @return Origin of this type or empty Origin of UNKNOWN type if not found
     */
    public Origin firstOfType(OriginType originType) {
        for (Origin origin : mOrigins.values()) {
            if (origin.getOriginType() == originType) {
                return origin;
            }
        }
        return Origin.getEmpty();
    }

    public Collection<Origin> collection() {
        return mOrigins.values();
    }
    
    public boolean isHtmlContentAllowed(long originId) {
        return fromId(originId).isHtmlContentAllowed();
    }

    public List<Origin> originsToSync(Origin originIn, boolean forAllOrigins, boolean isSearch) {
        boolean hasSynced = hasSyncedForAllOrigins(isSearch);
        List<Origin> origins = new ArrayList<>();
        if (forAllOrigins) {
            for (Origin origin : collection()) {
                addMyOriginToSync(origins, origin, isSearch, hasSynced);
            }
        } else {
            addMyOriginToSync(origins, originIn, isSearch, false);
        }
        return origins;
    }

    public boolean hasSyncedForAllOrigins(boolean isSearch) {
        for (Origin origin : mOrigins.values()) {
            if (origin.isSyncedForAllOrigins(isSearch)) {
                return true;
            }
        }
        return false;
    }

    private void addMyOriginToSync(List<Origin> origins, Origin origin, boolean isSearch, boolean hasSynced) {
        if ( !origin.isValid()) {
            return;
        }
        if (hasSynced && !origin.isSyncedForAllOrigins(isSearch)) {
            return;
        }
        origins.add(origin);
    }

    public boolean isGlobalSearchSupported(Origin origin, boolean forAllOrigins) {
        return originsForGlobalSearch(origin, forAllOrigins).size() > 0;
    }

    public List<Origin> originsForGlobalSearch(Origin originIn, boolean forAllOrigins) {
        List<Origin> origins = new ArrayList<>();
        if (forAllOrigins) {
            for (MyAccount account : myContext.persistentAccounts().collection()) {
                if (account.getOrigin().isInCombinedGlobalSearch() &&
                        account.isValidAndSucceeded() && account.isGlobalSearchSupported()
                        && !origins.contains(account.getOrigin())) {
                    origins.add(account.getOrigin());
                }
            }
        } else if (originIn != null && originIn.isValid()) {
            MyAccount account = myContext.persistentAccounts().getFirstSucceededForOriginId(originIn.getId());
            if (account.isValidAndSucceeded() && account.isGlobalSearchSupported()) {
                origins.add(originIn);
            }
        }
        return origins;
    }
}
