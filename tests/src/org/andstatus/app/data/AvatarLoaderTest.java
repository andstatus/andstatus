package org.andstatus.app.data;

import android.content.ContentValues;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;

import org.andstatus.app.CommandData;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.URL;

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
        loader.removeOld();
        assertEquals("Not loaded yet", AvatarStatus.ABSENT, loader.getStatusAndRowId(new URL(urlStringOld)));
        loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.LOADED, false);
        
        String urlString = "https://andstatus.org/nonexistent_avatar.png";
        changeUserAvatarUrl(urlString);
        loadAndAssertStatusForUrl(urlString, AvatarStatus.SOFT_ERROR, false);
        
        urlString = "https://raw.github.com/andstatus/andstatus/master/res/drawable/notification_icon.png";
        changeUserAvatarUrl(urlString);
        loadAndAssertStatusForUrl(urlString, AvatarStatus.LOADED, false);

        changeUserAvatarUrl("");
        loadAndAssertStatusForUrl("", AvatarStatus.HARD_ERROR, false);
        
        changeUserAvatarUrl(urlStringOld);
        long rowIdError = loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.SOFT_ERROR, true);
        long rowIdRecovered = loadAndAssertStatusForUrl(urlStringOld, AvatarStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    private void changeUserAvatarUrl(String urlString) {
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
            assertEquals("Loaded", status, loader.getStatusAndRowId(new URL(urlString)));
            assertTrue(avatarDrawable.isLoaded());
            assertFalse(commandData.commandResult.hasError());
        } else {
            assertFalse(avatarDrawable.isLoaded());
            assertTrue(commandData.commandResult.hasError());
        }
        return loader.getRowId();
    }
}
