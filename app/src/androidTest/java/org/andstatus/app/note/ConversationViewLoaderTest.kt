package org.andstatus.app.note;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertTrue;

public class ConversationViewLoaderTest implements ProgressPublisher {
    private Origin origin = Origin.EMPTY;
    private long selectedNoteId;
    private long progressCounter = 0;

    @Before
    public void setUp() throws Exception {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        origin = demoData.getMyAccount(demoData.conversationAccountName).getOrigin();
        assertTrue(origin.isValid());
        selectedNoteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), demoData.conversationEntryNoteOid);
        assertTrue("Selected note exists", selectedNoteId != 0);
        MyLog.i(this, "setUp ended");
    }

    @Test
    public void testLoad() {
        ConversationLoader loader = new ConversationLoaderFactory().getLoader(
                ConversationViewItem.EMPTY, myContextHolder.getNow(), origin, selectedNoteId, false);
        progressCounter = 0;
        loader.load(this);
        List<ConversationViewItem> list = loader.getList();
        assertTrue("List is empty", !list.isEmpty());
        boolean indentFound = false;
        boolean orderFound = false;
        for( ConversationViewItem oMsg : list) {
            if (oMsg.indentLevel > 0) {
                indentFound = true;
            }
            if (oMsg.mListOrder != 0) {
                orderFound = true;
            }
        }
        assertTrue("Indented note found in " + list, indentFound);
        assertTrue("Ordered note found in " + list, orderFound);
        assertTrue(progressCounter > 0);
    }

    @Override
    public void publish(String progress) {
        progressCounter++;
    }
}
