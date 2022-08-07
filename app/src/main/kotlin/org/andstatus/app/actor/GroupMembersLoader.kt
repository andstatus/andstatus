/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.actor

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.GroupMembership
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin

/**
 * @author yvolk@yurivolkov.com
 */
class GroupMembersLoader(
    myContext: MyContext,
    actorsScreenType: ActorsScreenType,
    origin: Origin,
    val actorIn: Actor, searchQuery: String
) : ActorsLoader(
    myContext,
    if (actorsScreenType.groupType == GroupType.NOT_A_GROUP)
        throw IllegalArgumentException("Not a group: $actorsScreenType") else actorsScreenType,
    origin, actorIn.actorId, searchQuery
) {

    override fun getSqlActorIds(): String {
        return if (actorsScreenType.groupType.isSingleForParent)
            " IN (" + GroupMembership.selectSingleGroupMemberIds(
                listOf(actorIn.actorId),
                actorsScreenType.groupType,
                false
            ) + ")"
        else
            " IN (" + GroupMembership.selectGroupMemberIds(actorIn) + ")"
    }

    override fun getSubtitle(): String {
        return ma.toAccountButtonText()
    }
}
