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

import android.content.ContentValues
import android.provider.BaseColumns
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.AvatarData
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class AvatarDownloaderTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)

    @Test
    fun testLoadPumpio() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(DemoData.demoData.conversationAccountName + " exists", ma.isValid)
        loadForOneMyAccount(ma, DemoData.demoData.conversationAccountAvatarUrl)
    }

    @Test
    fun testLoadBasicAuth() {
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        Assert.assertTrue(DemoData.demoData.gnusocialTestAccountName + " exists", ma.isValid)
        loadForOneMyAccount(ma, DemoData.demoData.gnusocialTestAccountAvatarUrl)
    }

    private fun loadForOneMyAccount(ma: MyAccount, urlStringInitial: String?) {
        changeAvatarUrl(ma.actor, urlStringInitial)
        DownloadData.Companion.deleteAllOfThisActor(ma.myContext, ma.actorId)
        val loader: FileDownloader = AvatarDownloader(ma.actor)
        Assert.assertEquals("Not loaded yet", DownloadStatus.ABSENT, loader.getStatus())
        loadAndAssertStatusForMa(ma, "First loading",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        var urlString = "http://andstatus.org/nonexistent_avatar_" + System.currentTimeMillis() + ".png"
        changeMaAvatarUrl(ma, urlString)
        loadAndAssertStatusForMa(ma, "Non-existent file is a hard error",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, false)
        urlString = "https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable-mdpi/notification_icon.png"
        changeMaAvatarUrl(ma, urlString)
        loadAndAssertStatusForMa(ma, "URL changed",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        deleteMaAvatarFile(ma)
        loadAndAssertStatusForMa(ma, "Avatar was deleted",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        deleteMaAvatarFile(ma)
        changeAvatarStatus(ma.actor, DownloadStatus.HARD_ERROR)
        loadAndAssertStatusForMa(ma, "Reload even after hard error", DownloadStatus.LOADED,
                DownloadStatus.LOADED, false)
        changeAvatarStatus(ma.actor, DownloadStatus.SOFT_ERROR)
        loadAndAssertStatusForMa(ma, "Reload on Soft error",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        changeMaAvatarUrl(ma, "")
        loadAndAssertStatusForMa(ma, "In a case avatar removed from actor, we see the last loaded",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        changeMaAvatarUrl(ma, "http://example.com/inexistent.jpg")
        loadAndAssertStatusForMa(ma, "Inexistent avatar",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, false)
        val aLoader = ActorsLoader(myContext, ActorsScreenType.ACTORS_AT_ORIGIN,
                ma.origin, 0, "")
        aLoader.addActorToList(ma.actor)
        aLoader.load()
        val viewItem = aLoader.getList()[0]
        Assert.assertTrue("Should show previous avatar " + viewItem.actor,
                viewItem.getAvatarFile().getSize().x > 0)
        changeMaAvatarUrl(ma, urlStringInitial)
        val rowIdError = loadAndAssertStatusForMa(ma, "Restored avatar URL",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, true)
        val rowIdRecovered = loadAndAssertStatusForMa(ma, "Reloading avatar",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        Assert.assertEquals("Updated the same row ", rowIdError, rowIdRecovered)
    }

    private fun deleteMaAvatarFile(ma: MyAccount) {
        val data: DownloadData = AvatarData.Companion.getCurrentForActor(ma.actor)
        Assert.assertTrue("Loaded avatar file deleted", data.getFile().delete())
    }

    @Test
    fun testDeletedFile() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        changeMaAvatarUrl(ma, DemoData.demoData.conversationAccountAvatarUrl)
        val urlString = MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, ma.actorId)
        Assert.assertEquals(DemoData.demoData.conversationAccountAvatarUrl, urlString)
        loadAndAssertStatusForMa(ma, "", DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        var data: DownloadData = AvatarData.Companion.getCurrentForActor(ma.actor)
        Assert.assertTrue("Existence of " + data.getFilename(), data.getFile().existed)
        Assert.assertTrue("Is File" + data.getFilename(), data.getFile().getFile()?.isFile == true)
        val avatarFile = data.getFile()
        DownloadData.Companion.deleteAllOfThisActor(ma.myContext, ma.actorId)
        Assert.assertFalse(avatarFile.existsNow())
        loadAndAssertStatusForMa(ma, "", DownloadStatus.LOADED, DownloadStatus.LOADED, false)
        data = AvatarData.Companion.getCurrentForActor(ma.actor)
        Assert.assertTrue(data.getFile().existed)
    }

    private fun changeMaAvatarUrl(ma: MyAccount, urlString: String?) {
        changeAvatarUrl(ma.actor, urlString)
    }

    private fun changeAvatarStatus(actor: Actor, status: DownloadStatus) {
        val values = ContentValues()
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save())
        values.put(DownloadTable.DOWNLOADED_DATE, MyLog.uniqueCurrentTimeMS())
        myContext.database
            ?.update(
                DownloadTable.TABLE_NAME, values, DownloadTable.ACTOR_ID + "=" + actor.actorId
                        + " AND " + DownloadTable.URL + "=" + MyQuery.quoteIfNotQuoted(actor.getAvatarUrl()), null
            )
        val actor2: Actor =  myContext.users.reload(actor)
        val avatarData: AvatarData = AvatarData.Companion.getCurrentForActor(actor)
        Assert.assertEquals("Download status for $actor2", status, avatarData.getStatus())
    }

    private fun loadAndAssertStatusForMa(ma: MyAccount, description: String?, loadStatus: DownloadStatus,
                                         displayedStatus: DownloadStatus, mockNetworkError: Boolean): Long {
        TestSuite.clearHttpMocks()
        val actor: Actor = Actor.Companion.load(myContext, ma.actor.actorId)
        val loader: FileDownloader = AvatarDownloader(actor)
        if (mockNetworkError) {
            loader.setConnectionMock(ConnectionMock.newFor(ma)
                    .withException(ConnectionException(StatusCode.NOT_FOUND, "Mocked IO exception")).connection)
        }
        val commandData: CommandData = CommandData.Companion.newActorCommand(CommandEnum.GET_AVATAR, actor, actor.getUsername())
        val loaded = loader.load(commandData)
        val data: DownloadData = AvatarData.Companion.getDisplayedForActor(actor)
        val logMsg = """$description Expecting load status: $loadStatus, displayed: $displayedStatus
  for $actor
  (loaded $data, error message:'${commandData.getResult().getMessage()}')${if (mockNetworkError) "mocked the error" else ""}"""
        if (mockNetworkError || loadStatus == DownloadStatus.HARD_ERROR) {
            Assert.assertTrue("Load should be a failure: $logMsg", loaded.isFailure)
        }
        Assert.assertEquals("Checking load status: $logMsg", loadStatus, loader.getStatus())
        if (DownloadStatus.LOADED == loadStatus) {
            Assert.assertFalse("Should be no errors: $logMsg", commandData.getResult().hasError())
        } else {
            Assert.assertTrue("Should be an error: $logMsg", commandData.getResult().hasError())
        }
        Assert.assertEquals(logMsg, loadStatus, loader.getStatus())
        if (DownloadStatus.LOADED == displayedStatus) {
            Assert.assertTrue("Avatar should be displayed: $logMsg", data.getFile().existed)
        } else {
            Assert.assertFalse("Avatar shouldn't be diplayed: $logMsg", data.getFile().existed)
        }
        return loader.data.getDownloadId()
    }

    companion object {
        fun changeAvatarUrl(actor: Actor, urlString: String?) {
            val myContext = actor.origin.myContext
            val values = ContentValues()
            actor.setAvatarUrl(urlString)
            actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
            values.put(ActorTable.AVATAR_URL, urlString)
            values.put(ActorTable.UPDATED_DATE, actor.getUpdatedDate())
             myContext.database
                    ?.update(ActorTable.TABLE_NAME, values, BaseColumns._ID + "=" + actor.actorId, null)
             myContext.users.reload(actor)
            val loadedActor = Actor.Companion.load(myContext, actor.actorId)
            Assert.assertEquals("URL should change for $actor reloaded: $loadedActor",
                    urlString, MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, actor.actorId))
        }
    }
}
