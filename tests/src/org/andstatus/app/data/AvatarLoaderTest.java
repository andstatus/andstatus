package org.andstatus.app.data;

import android.content.ContentValues;
import android.test.InstrumentationTestCase;

import org.andstatus.app.CommandData;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.io.IOException;

public class AvatarLoaderTest extends InstrumentationTestCase {
    private MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initialize(this);
        TestSuite.enshureDataAdded();
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma != null);
        MyLog.i(this, "setUp ended");
    }

    public void testLoad() throws IOException {
        String urlStringOld = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlStringOld);
        AvatarLoader loader = new AvatarLoader(ma.getUserId());
        AvatarDrawable avatarDrawable = new AvatarDrawable(ma.getUserId(), loader.getFileName());
        if (avatarDrawable.exists()) {
            avatarDrawable.getFile().delete();
        }
        loader = new AvatarLoader(ma.getUserId());
        assertEquals("Not loaded yet", AvatarStatus.ABSENT, loader.getStatus());
        loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.LOADED, false);
        
        String urlString = "http://andstatus.org/nonexistent_avatar.png";
        changeMaAvatarUrl(urlString);
        loadAndAssertStatusForUrl(urlString, AvatarStatus.SOFT_ERROR, false);
        
        urlString = "https://raw.github.com/andstatus/andstatus/master/res/drawable/notification_icon.png";
        changeMaAvatarUrl(urlString);
        loadAndAssertStatusForUrl(urlString, AvatarStatus.LOADED, false);

        changeMaAvatarUrl("");
        loadAndAssertStatusForUrl("", AvatarStatus.HARD_ERROR, false);
        
        changeMaAvatarUrl(urlStringOld);
        long rowIdError = loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.SOFT_ERROR, true);
        long rowIdRecovered = loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    public void testDeletedFile() throws IOException {
        changeMaAvatarUrl(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);
        String urlString = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlString);
        
        loadAndAssertStatusForUrl(urlString, AvatarStatus.LOADED, false);
        AvatarLoader loader = new AvatarLoader(ma.getUserId());
        File avatarFile = new File(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_AVATARS, null), loader.getFileName());
        assertTrue("Existance of " + avatarFile.getCanonicalPath(), avatarFile.exists());
        assertTrue("Is File" + avatarFile.getCanonicalPath(), avatarFile.isFile());
        assertTrue("Deleting " + avatarFile.getCanonicalPath(), avatarFile.delete());
        assertFalse(avatarFile.exists());
        AvatarDrawable avatarDrawable = new AvatarDrawable(ma.getUserId(), loader.getFileName());
        assertFalse(avatarDrawable.exists());

        loadAndAssertStatusForUrl(urlString, AvatarStatus.LOADED, false);
        loader = new AvatarLoader(ma.getUserId());
        avatarDrawable = new AvatarDrawable(ma.getUserId(), loader.getFileName());
        assertTrue(avatarDrawable.exists());
    }
    
    private void changeMaAvatarUrl(String urlString) {
        ContentValues values = new ContentValues();
        values.put(User.AVATAR_URL, urlString);
        MyContextHolder.get().getDatabase().getWritableDatabase()
                .update(User.TABLE_NAME, values, User._ID + "=" + ma.getUserId(), null);
    }
    
    private long loadAndAssertStatusForUrl(String urlString, AvatarStatus status, boolean mockNetworkError) throws IOException {
        AvatarLoader loader = new AvatarLoader(ma.getUserId());
        loader.mockNetworkError = mockNetworkError;
        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        loader.load(commandData);
        AvatarDrawable avatarDrawable = new AvatarDrawable(ma.getUserId(), loader.getFileName());
        if (AvatarStatus.LOADED.equals(status)) {
            assertFalse(commandData.commandResult.hasError());
            assertEquals("Loaded " + urlString, status, loader.getStatus());
            assertTrue(urlString, avatarDrawable.exists());
        } else {
            assertTrue(commandData.commandResult.hasError());
            assertFalse(avatarDrawable.exists());
        }
        return loader.getRowId();
    }
}
