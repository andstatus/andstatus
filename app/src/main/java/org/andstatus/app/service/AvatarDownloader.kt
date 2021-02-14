/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.AvatarData
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog

class AvatarDownloader(myContext: MyContext?, data: DownloadData?) : FileDownloader(myContext, data) {
    constructor(actor: Actor?) : this(actor.origin.myContext, AvatarData.Companion.getCurrentForActor(actor)) {}

    override fun findBestAccountForDownload(): MyAccount? {
        val origin: Origin = MyContextHolder.Companion.myContextHolder.getNow().origins().fromId(
                MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID, data.actorId))
        return MyContextHolder.Companion.myContextHolder.getNow().accounts().getFirstPreferablySucceededForOrigin(origin)
    }

    override fun onSuccessfulLoad() {
        data.deleteOtherOfThisActor(MyContextHolder.Companion.myContextHolder.getNow())
        MyContextHolder.Companion.myContextHolder.getNow().users().load(data.actorId, true)
        MyLog.v(this) { "Loaded avatar actorId:" + data.actorId + "; uri:'" + data.uri + "'" }
    }
}