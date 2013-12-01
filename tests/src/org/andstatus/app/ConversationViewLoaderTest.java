package org.andstatus.app;

import android.content.Context;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class ConversationViewLoaderTest extends InstrumentationTestCase {
    private MyAccount ma;
    private long selectedMessageId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initialize(this);
        TestSuite.enshureDataAdded();
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma != null);
        selectedMessageId = MyProvider.oidToId(OidEnum.MSG_OID, TestSuite.CONVERSATION_ACCOUNT_ORIGIN.getId(), TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        assertTrue("Selected message exists", selectedMessageId != 0);
        MyLog.i(this, "setUp ended");
    }

    public void testLoad() {
        Context context = MyContextHolder.get().context();
        ConversationViewLoader loader = new ConversationViewLoader(context, ma, selectedMessageId, null);
        loader.load();
        List<ConversationOneMessage> list = loader.getMsgs();
        assertTrue(!list.isEmpty());
    }
}
