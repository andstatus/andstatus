package org.andstatus.app.data;

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
        String urlString = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlString);
        AvatarLoader loader = new AvatarLoader(ma.getUserId());
        loader.removeOld();
        assertEquals("Not loaded yet", AvatarStatus.ABSENT, loader.getStatusAndRowId(new URL(urlString)));
        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        loader.load(commandData);
        assertEquals("Loaded", AvatarStatus.LOADED, loader.getStatusAndRowId(new URL(urlString)));
        AvatarDrawable avatarDrawable = new AvatarDrawable(ma.getUserId(), loader.getFileName());
        assertTrue(avatarDrawable.isLoaded());
    }
}
