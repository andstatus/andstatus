package org.andstatus.app.origin;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveredOrigins {
    private static final Map<String,Origin> mOrigins = new ConcurrentHashMap<String, Origin>();

    private DiscoveredOrigins() {
        // Empty
    }
    
    public static void addAll(List<Origin> newOrigins) {
        if (newOrigins.isEmpty()) {
            return;
        }
        int oldCount = 0;
        OriginType type = newOrigins.get(0).getOriginType();
        for (Origin origin : mOrigins.values()) {
            if (origin.getOriginType() == type) {
                mOrigins.remove(origin);
                oldCount++;
            }
        }
        for (Origin origin : newOrigins) {
            mOrigins.put(origin.getName(), origin);
        }
        MyLog.v(DiscoveredOrigins.class, "Removed " + oldCount + " and added " + newOrigins.size() + " " + type.name() + " origins");
    }

    public static void clear() {
        mOrigins.clear();
    }
    
    public static Collection<Origin> get() {
        return mOrigins.values();
    }

    public static Origin fromName(String originName) {
        if (!StringUtils.isEmpty(originName) && mOrigins.containsKey(originName)) {
            return mOrigins.get(originName);
        } else {
            return Origin.EMPTY;
        }
    }
}
