package org.andstatus.app;

import android.content.Context;
import android.test.InstrumentationTestCase;

import org.andstatus.app.ConversationViewLoader.progressPublisher;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class ConversationViewLoaderTest extends InstrumentationTestCase implements progressPublisher {
    private MyAccount ma;
    private long selectedMessageId;
    private long progressCounter = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        selectedMessageId = MyProvider.oidToId(OidEnum.MSG_OID, ma.getOriginId(), TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        assertTrue("Selected message exists", selectedMessageId != 0);
        MyLog.i(this, "setUp ended");
    }

    public void testLoad() {
        Context context = MyContextHolder.get().context();
        ConversationViewLoader loader = new ConversationViewLoader(context, ma, selectedMessageId);
        progressCounter = 0;
        loader.load(this);
        List<ConversationOneMessage> list = loader.getMsgs();
        assertTrue("List is not empty", !list.isEmpty());
        boolean indentFound = false;
        boolean orderFound = false;
        for( ConversationOneMessage oMsg : list) {
            if (oMsg.mIndentLevel > 0) {
                indentFound = true;
            }
            if (oMsg.mListOrder != 0) {
                orderFound = true;
            }
        }
        assertTrue("Indented message found", indentFound);
        assertTrue("Ordered message found", orderFound);
        assertTrue(progressCounter > 0);
    }

    @Override
    public void publish(String progress) {
        progressCounter++;
    }
}
