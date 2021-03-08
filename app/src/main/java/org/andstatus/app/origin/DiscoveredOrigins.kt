package org.andstatus.app.origin

import org.andstatus.app.util.MyLog
import java.util.concurrent.ConcurrentHashMap

object DiscoveredOrigins {
    private val mOrigins: MutableMap<String?, Origin?>? = ConcurrentHashMap()
    fun replaceAll(newOrigins: MutableList<Origin?>?) {
        if (newOrigins.isEmpty()) {
            return
        }
        var oldCount = 0
        val type = newOrigins.get(0).getOriginType()
        for (origin in mOrigins.values) {
            if (origin.originType === type) {
                mOrigins.remove(origin.getName())
                oldCount++
            }
        }
        for (origin in newOrigins) {
            mOrigins[origin.getName()] = origin
        }
        val removed = oldCount
        MyLog.v(DiscoveredOrigins::class.java) {
            ("Removed " + removed + " and added " + newOrigins.size
                    + " " + type.name + " origins")
        }
    }

    fun clear() {
        mOrigins.clear()
    }

    fun get(): MutableCollection<Origin?>? {
        return mOrigins.values
    }

    fun fromName(originName: String?): Origin? {
        return if (!originName.isNullOrEmpty() && mOrigins.containsKey(originName)) {
            mOrigins.get(originName)
        } else {
             Origin.EMPTY
        }
    }
}