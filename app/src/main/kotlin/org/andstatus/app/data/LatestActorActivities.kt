/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.data

import java.util.HashMap
/**
 * Collects [ActorActivity] data (e.g. during timeline download) and allows to save it in bulk
 * @author yvolk@yurivolkov.com
 */
class LatestActorActivities {
    private val actorActivities: MutableMap<Long, ActorActivity> = HashMap()

    /**
     * Add information about new Actor's activity
     */
    fun onNewActorActivity(uaIn: ActorActivity) {
        // On different implementations see 
        // http://stackoverflow.com/questions/81346/most-efficient-way-to-increment-a-map-value-in-java
        var um = actorActivities[uaIn.getActorId()]
        if (um == null) {
            um = uaIn
        } else {
            um.onNewActivity(uaIn.getLastActivityId(), uaIn.getLastActivityDate())
        }
        actorActivities[um.getActorId()] = um
    }

    /**
     * Persist all information into the database
     * @return true if succeeded for all entries
     */
    fun save(): Boolean {
        var ok = true
        for (um in actorActivities.values) {
            if (!um.save()) {
                ok = false
            }
        }
        return ok
    }
}
