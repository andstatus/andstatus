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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AvatarData;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AvatarDownloaderTest {
    private MyAccount ma = MyAccount.EMPTY;

    @Before
    public void setUp() throws Exception {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        MyLog.i(this, "setUp ended");
    }

    @Test
    public void testLoadPumpio() throws IOException {
        ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(demoData.conversationAccountName + " exists", ma.isValid());
        loadForOneMyAccount(demoData.conversationAccountAvatarUrl);
    }

    @Test
    public void testLoadBasicAuth() throws IOException {
        ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        assertTrue(demoData.gnusocialTestAccountName + " exists", ma.isValid());
        loadForOneMyAccount(demoData.gnusocialTestAccountAvatarUrl);
    }
    
    private void loadForOneMyAccount(String urlStringInitial) throws IOException {
        String urlString1 = MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, ma.getActorId());
        assertEquals(urlStringInitial, urlString1);
        
        AvatarData.deleteAllOfThisActor(ma.getActorId());
        
        FileDownloader loader = new AvatarDownloader(ma.getActorId());
        assertEquals("Not loaded yet", DownloadStatus.ABSENT, loader.getStatus());
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        String urlString = "http://andstatus.org/nonexistent_avatar_" + System.currentTimeMillis() +  ".png";
        changeMaAvatarUrl(urlString);
        // Non-existent file is a hard error
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);
        
        urlString = "https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable-mdpi/notification_icon.png";
        assertEquals("Changed 1 row ", 1, changeMaAvatarUrl(urlString));
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);

        deleteMaAvatar();
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        deleteMaAvatar();
        assertEquals("Changed 1 row ", 1, changeMaAvatarStatus(urlString, DownloadStatus.HARD_ERROR));
        // Don't reload if hard error
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);

        changeMaAvatarStatus(urlString, DownloadStatus.SOFT_ERROR);
        // Reload on Soft error
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        changeMaAvatarUrl("");
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);
        
        changeMaAvatarUrl(urlStringInitial);
        long rowIdError = loadAndAssertStatusForMa(DownloadStatus.ABSENT, true);
        long rowIdRecovered = loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    private void deleteMaAvatar() {
        DownloadData data = AvatarData.getForActor(ma.getActorId());
        assertTrue("Loaded avatar file deleted", data.getFile().delete());
    }

    @Test
    public void testDeletedFile() throws IOException {
        ma = demoData.getMyAccount(demoData.conversationAccountName);
        
        changeMaAvatarUrl(demoData.conversationAccountAvatarUrl);
        String urlString = MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, ma.getActorId());
        assertEquals(demoData.conversationAccountAvatarUrl, urlString);
        
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        DownloadData data = AvatarData.getForActor(ma.getActorId());
        assertTrue("Existence of " + data.getFilename(), data.getFile().exists());
        assertTrue("Is File" + data.getFilename(), data.getFile().getFile().isFile());

        DownloadFile avatarFile = data.getFile();
        AvatarData.deleteAllOfThisActor(ma.getActorId());
        assertFalse(avatarFile.exists());

        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        data = AvatarData.getForActor(ma.getActorId());
        assertTrue(data.getFile().exists());
    }
    
    private int changeMaAvatarUrl(String urlString) {
        return changeAvatarUrl(ma.getActor(), urlString);
    }

    static int changeAvatarUrl(Actor actor, String urlString) {
        ContentValues values = new ContentValues();
        values.put(ActorTable.AVATAR_URL, urlString);
        return MyContextHolder.get().getDatabase()
                .update(ActorTable.TABLE_NAME, values, ActorTable._ID + "=" + actor.actorId, null);
    }

    private int changeMaAvatarStatus(String urlString, DownloadStatus status) throws MalformedURLException {
        URL url = new URL(urlString); 
        ContentValues values = new ContentValues();
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
        return MyContextHolder.get().getDatabase()
                .update(DownloadTable.TABLE_NAME, values, DownloadTable.ACTOR_ID + "=" + ma.getActorId()
                        + " AND " + DownloadTable.URI + "=" + MyQuery.quoteIfNotQuoted(url.toExternalForm()), null);
    }

    private long loadAndAssertStatusForMa(DownloadStatus status, boolean mockNetworkError) {
        FileDownloader loader = new AvatarDownloader(ma.getActorId());
        if (mockNetworkError) {
            loader.connectionMock = new ConnectionTwitterGnuSocialMock(new ConnectionException("Mocked IO exception"));
        }
        CommandData commandData = CommandData.newCommand(CommandEnum.GET_AVATAR);
        loader.load(commandData);

        DownloadData data = AvatarData.getForActor(ma.getActorId());
        if (DownloadStatus.LOADED.equals(status)) {
            assertFalse("Loaded " + data + ", error message:" + commandData.getResult().getMessage(),
                    commandData.getResult()
                    .hasError());
            assertEquals("Loaded " + data.getUri() + ", error message:" + commandData.getResult().getMessage(), status,
                    loader.getStatus());
        } else {
            assertTrue("Error loading " + data.getUri(), commandData.getResult().hasError());
        }
        
        if (DownloadStatus.LOADED.equals(status)) {
            assertTrue("Exists avatar " + data.getUri(), data.getFile().exists());
        } else {
            assertFalse("Doesn't exist avatar " + data.getUri(), data.getFile().exists());
        }

        assertEquals("Loaded '" + data.getUri() + "'", status, loader.getStatus());
        
        return data.getDownloadId();
    }
}
