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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.SearchObjects;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextImpl;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;

public class PersistentOrigins {
    private final MyContext myContext;
    private final Map<String,Origin> mOrigins = new ConcurrentHashMap<String, Origin>();
    
    private PersistentOrigins(MyContextImpl myContext) {
        this.myContext = myContext;
    }

    public boolean initialize() {
        return initialize(myContext.getDatabase());
    }
    
    public boolean initialize(SQLiteDatabase db) {
        String sql = "SELECT * FROM " + OriginTable.TABLE_NAME;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            mOrigins.clear();
            while (cursor.moveToNext()) {
                Origin origin = new Origin.Builder(myContext, cursor).build();
                mOrigins.put(origin.name, origin);
            }
        } catch (SQLException e){
            MyLog.e(this, "Failed to initialize origins\n" + MyLog.getStackTrace(e));
            return false;
        } finally {
            DbUtils.closeSilently(cursor);
        }
        
        MyLog.v(this, () -> "Initialized " + mOrigins.size() + " origins");
        return true;
    }
    
    public static PersistentOrigins newEmpty(MyContextImpl myContext) {
        return new PersistentOrigins(myContext);
    }

    /**
     * @return EMPTY Origin if not found
     */
    @NonNull
    public Origin fromId(long originId) {
        if (originId == 0) return Origin.EMPTY;

        for (Origin origin : mOrigins.values()) {
            if (origin.id == originId) {
                return origin;
            }
        }
        return Origin.EMPTY;
    }
    
    /**
     * @return Origin of UNKNOWN type if not found
     */
    public Origin fromName(String originName) {
        Origin origin = null;
        if (!StringUtil.isEmpty(originName)) {
            origin = mOrigins.get(originName);
        }
        return origin == null ? Origin.EMPTY : origin;
    }

    public Origin fromOriginInAccountNameAndHost(String originInAccountName, String host) {
        List<Origin> origins = allFromOriginInAccountNameAndHost(originInAccountName, host);
        switch (origins.size()) {
            case 0:
                return Origin.EMPTY;
            case 1:
                return origins.get(0);
            default:
                // Select Origin that was added earlier
                return origins.stream().min((o1, o2) -> Long.signum(o1.id - o2.id)).orElse(Origin.EMPTY);
        }
    }

    public List<Origin> allFromOriginInAccountNameAndHost(String originInAccountName, String host) {
        List<Origin> origins = fromOriginInAccountName(originInAccountName);
        switch (origins.size()) {
            case 0:
            case 1:
                return origins;
            default:
                return origins.stream()
                    .filter(origin -> origin.getAccountNameHost().isEmpty() ||
                                    origin.getAccountNameHost().equalsIgnoreCase(host))
                    .collect(Collectors.toList());
        }
    }

    public List<Origin> fromOriginInAccountName(String originInAccountName) {
        return StringUtil.optNotEmpty(originInAccountName).map(name -> {
            OriginType originType = OriginType.fromTitle(name);
            List<Origin> originsOfType =  originType == OriginType.UNKNOWN
                    ? Collections.emptyList()
                    : mOrigins.values().stream().filter(origin -> origin.getOriginType() == originType)
                        .collect(Collectors.toList());
            if (originsOfType.size() == 1) {
                return originsOfType;
            }

            List<Origin> originsWithName = mOrigins.values().stream()
                    .filter(origin -> origin.getName().equalsIgnoreCase(name)
                        && (originType == OriginType.UNKNOWN || origin.getOriginType() == originType))
                    .collect(Collectors.toList());
            return originsOfType.size() > originsWithName.size()
                    ? originsOfType
                    : originsWithName;
        })
        .orElse(Collections.emptyList());
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
        return Origin.EMPTY;
    }

    public Collection<Origin> collection() {
        return mOrigins.values();
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

    public boolean isSearchSupported(SearchObjects searchObjects, Origin origin, boolean forAllOrigins) {
        return originsForInternetSearch(searchObjects, origin, forAllOrigins).size() > 0;
    }

    @NonNull
    public List<Origin> originsForInternetSearch(SearchObjects searchObjects, Origin originIn, boolean forAllOrigins) {
        List<Origin> origins = new ArrayList<>();
        if (forAllOrigins) {
            for (MyAccount account : myContext.accounts().get()) {
                if (account.getOrigin().isInCombinedGlobalSearch() &&
                        account.isValidAndSucceeded() && account.isSearchSupported(searchObjects)
                        && !origins.contains(account.getOrigin())) {
                    origins.add(account.getOrigin());
                }
            }
        } else if (originIn != null && originIn.isValid()) {
            MyAccount account = myContext.accounts().getFirstSucceededForOrigin(originIn);
            if (account.isValidAndSucceeded() && account.isSearchSupported(searchObjects)) {
                origins.add(originIn);
            }
        }
        return origins;
    }

    @NonNull
    public List<Origin> originsOfType(@NonNull OriginType originType) {
        List<Origin> origins = new ArrayList<>();
        for (Origin origin : collection()) {
            if (origin.getOriginType().equals(originType)) {
                origins.add(origin);
            }
        }
        return origins;
    }
}
