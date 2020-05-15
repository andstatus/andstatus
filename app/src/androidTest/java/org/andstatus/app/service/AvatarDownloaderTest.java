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

package org.andstatus.app.service;

import android.content.ContentValues;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AvatarData;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import io.vavr.control.Try;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AvatarDownloaderTest {

    @Before
    public void setUp() throws Exception {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);
        MyLog.i(this, "setUp ended");
    }

    @Test
    public void testLoadPumpio() {
        MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(demoData.conversationAccountName + " exists", ma.isValid());
        loadForOneMyAccount(ma, demoData.conversationAccountAvatarUrl);
    }

    @Test
    public void testLoadBasicAuth() {
        MyAccount ma = demoData.getGnuSocialAccount();
        assertTrue(demoData.gnusocialTestAccountName + " exists", ma.isValid());
        loadForOneMyAccount(ma, demoData.gnusocialTestAccountAvatarUrl);
    }
    
    private void loadForOneMyAccount(MyAccount ma, String urlStringInitial) {
        changeAvatarUrl(ma.getActor(), urlStringInitial);

        AvatarData.deleteAllOfThisActor(ma.getOrigin().myContext, ma.getActorId());
        FileDownloader loader = new AvatarDownloader(ma.getActor());
        assertEquals("Not loaded yet", DownloadStatus.ABSENT, loader.getStatus());

        loadAndAssertStatusForMa(ma, "First loading",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        
        String urlString = "http://andstatus.org/nonexistent_avatar_" + System.currentTimeMillis() +  ".png";
        changeMaAvatarUrl(ma, urlString);
        loadAndAssertStatusForMa(ma, "Non-existent file is a hard error",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, false);
        
        urlString = "https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable-mdpi/notification_icon.png";
        changeMaAvatarUrl(ma, urlString);
        loadAndAssertStatusForMa(ma, "URL changed",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);

        deleteMaAvatarFile(ma);
        loadAndAssertStatusForMa(ma, "Avatar was deleted",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        
        deleteMaAvatarFile(ma);
        changeAvatarStatus(ma.getActor(), DownloadStatus.HARD_ERROR);
        loadAndAssertStatusForMa(ma, "Reload even after hard error", DownloadStatus.LOADED,
                DownloadStatus.LOADED, false);

        changeAvatarStatus(ma.getActor(), DownloadStatus.SOFT_ERROR);
        loadAndAssertStatusForMa(ma, "Reload on Soft error",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        
        changeMaAvatarUrl(ma, "");
        loadAndAssertStatusForMa(ma, "In a case avatar removed from actor, we see the last loaded",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);

        changeMaAvatarUrl(ma, "http://example.com/inexistent.jpg");
        loadAndAssertStatusForMa(ma, "Inexistent avatar",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, false);

        ActorListLoader aLoader = new ActorListLoader(myContextHolder.getNow(), ActorListType.ACTORS_AT_ORIGIN,
                ma.getOrigin(), 0, "");
        aLoader.addActorToList(ma.getActor());
        aLoader.load(progress -> {});
        ActorViewItem viewItem = aLoader.getList().get(0);
        assertTrue("Should show previous avatar " + viewItem.getActor(),
                viewItem.getAvatarFile().getSize().x > 0);

        changeMaAvatarUrl(ma, urlStringInitial);
        long rowIdError = loadAndAssertStatusForMa(ma, "Restored avatar URL",
                DownloadStatus.HARD_ERROR, DownloadStatus.LOADED, true);
        long rowIdRecovered = loadAndAssertStatusForMa(ma, "Reloading avatar",
                DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    private void deleteMaAvatarFile(MyAccount ma) {
        DownloadData data = AvatarData.getCurrentForActor(ma.getActor());
        assertTrue("Loaded avatar file deleted", data.getFile().delete());
    }

    @Test
    public void testDeletedFile() {
        MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        
        changeMaAvatarUrl(ma, demoData.conversationAccountAvatarUrl);
        String urlString = MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, ma.getActorId());
        assertEquals(demoData.conversationAccountAvatarUrl, urlString);
        
        loadAndAssertStatusForMa(ma, "", DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        DownloadData data = AvatarData.getCurrentForActor(ma.getActor());
        assertTrue("Existence of " + data.getFilename(), data.getFile().existed);
        assertTrue("Is File" + data.getFilename(), data.getFile().getFile().isFile());

        DownloadFile avatarFile = data.getFile();
        AvatarData.deleteAllOfThisActor(ma.getOrigin().myContext, ma.getActorId());
        assertFalse(avatarFile.existsNow());

        loadAndAssertStatusForMa(ma, "", DownloadStatus.LOADED, DownloadStatus.LOADED, false);
        data = AvatarData.getCurrentForActor(ma.getActor());
        assertTrue(data.getFile().existed);
    }
    
    private void changeMaAvatarUrl(MyAccount ma, String urlString) {
        changeAvatarUrl(ma.getActor(), urlString);
    }

    static void changeAvatarUrl(Actor actor, String urlString) {
        ContentValues values = new ContentValues();
        actor.setAvatarUrl(urlString);
        actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        values.put(ActorTable.AVATAR_URL, urlString);
        values.put(ActorTable.UPDATED_DATE, actor.getUpdatedDate());
        myContextHolder.getNow().getDatabase()
                .update(ActorTable.TABLE_NAME, values, ActorTable._ID + "=" + actor.actorId, null);
        myContextHolder.getNow().users().reload(actor);
        assertEquals("URL should change for " + actor +
                        "\n reloaded: " + Actor.load(myContextHolder.getNow(), actor.actorId),
                urlString, MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, actor.actorId));
    }

    private void changeAvatarStatus(Actor actor, DownloadStatus status) {
        ContentValues values = new ContentValues();
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
        values.put(DownloadTable.DOWNLOADED_DATE, MyLog.uniqueCurrentTimeMS());
        myContextHolder.getNow().getDatabase()
                .update(DownloadTable.TABLE_NAME, values, DownloadTable.ACTOR_ID + "=" + actor.actorId
                        + " AND " + DownloadTable.URL + "=" + MyQuery.quoteIfNotQuoted(actor.getAvatarUrl()), null);
        Actor actor2 = myContextHolder.getNow().users().reload(actor);
        AvatarData avatarData = AvatarData.getCurrentForActor(actor);
        assertEquals("Download status for " + actor2, status, avatarData.getStatus());
    }

    private long loadAndAssertStatusForMa(MyAccount ma, String description, DownloadStatus loadStatus,
                                          DownloadStatus displayedStatus, boolean mockNetworkError) {
        TestSuite.clearHttpMocks();

        final Actor actor = Actor.load(myContextHolder.getBlocking(), ma.getActor().actorId);
        FileDownloader loader = new AvatarDownloader(actor);
        if (mockNetworkError) {
            loader.setConnectionMock(ConnectionMock.newFor(ma)
                    .withException(new ConnectionException(ConnectionException.StatusCode.NOT_FOUND,"Mocked IO exception"))
                    .connection);
        }
        CommandData commandData = CommandData.newActorCommand(CommandEnum.GET_AVATAR, actor, actor.getUsername());
        Try<Boolean> loaded = loader.load(commandData);

        DownloadData data = AvatarData.getDisplayedForActor(actor);
        String logMsg = description + " Expecting load status: " + loadStatus + ", displayed: " + displayedStatus +
                "\n  for " + actor + "\n  (loaded " + data +
                ", error message:'" + commandData.getResult().getMessage() + "')"
                + (mockNetworkError ? "mocked the error" : "");

        if (mockNetworkError || loadStatus == DownloadStatus.HARD_ERROR) {
            assertTrue("Load should be a failure: " + logMsg, loaded.isFailure());
        }
        assertEquals("Checking load status: " + logMsg, loadStatus, loader.getStatus());
        if (DownloadStatus.LOADED.equals(loadStatus)) {
            assertFalse("Should be no errors: " + logMsg, commandData.getResult().hasError());
        } else {
            assertTrue("Should be an error: " + logMsg, commandData.getResult().hasError());
        }
        assertEquals(logMsg, loadStatus, loader.getStatus());

        if (DownloadStatus.LOADED.equals(displayedStatus)) {
            assertTrue("Avatar should be displayed: " + logMsg, data.getFile().existed);
        } else {
            assertFalse("Avatar shouldn't be diplayed: " + logMsg, data.getFile().existed);
        }

        return loader.data.getDownloadId();
    }
}
