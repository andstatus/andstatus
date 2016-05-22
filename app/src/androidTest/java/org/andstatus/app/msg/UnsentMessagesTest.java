package org.andstatus.app.msg;

import android.provider.BaseColumns;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;

public class UnsentMessagesTest extends ActivityInstrumentationTestCase2<TimelineActivity> {
    final MyServiceTestHelper mService = new MyServiceTestHelper();
    MyAccount ma;

    public UnsentMessagesTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);

        mService.setUp(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
    }

    @Override
    protected void tearDown() throws Exception {
        mService.tearDown();
        super.tearDown();
    }


    public void testEditUnsentMessage() throws InterruptedException {
        final String method = "testEditUnsentMessage";
        String step = "Start editing a message";
        MyLog.v(this, method + " started");
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<TimelineActivity>(this, getActivity());
        View editorView = getActivity().findViewById(R.id.message_editor);
        helper.clickMenuItem(method + "; " + step, R.id.createMessageButton);
        ActivityTestHelper.waitViewVisible(method + "; " + step, editorView);

        String body = "Test unsent message, which we will try to edit " + TestSuite.TESTRUN_UID;
        EditText editText = (EditText) editorView.findViewById(R.id.messageBodyEditText);
        editText.requestFocus();
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync(body);
        TestSuite.waitForIdleSync(this);

        mService.serviceStopped = false;
        step = "Sending message";
        helper.clickMenuItem(method + "; " + step, R.id.messageSendButton);
        ActivityTestHelper.waitViewInvisible(method + "; " + step, editorView);

        mService.waitForServiceStopped(false);

        String condition = "BODY='" + body + "'";
        long unsentMsgId = MyQuery.conditionToLongColumnValue(MsgTable.TABLE_NAME, BaseColumns._ID, condition);
        step = "Unsent message " + unsentMsgId;
        assertTrue(method + "; " + step + ": " + condition, unsentMsgId != 0);
        assertEquals(method + "; " + step, DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, unsentMsgId)));

        step = "Start editing unsent message" + unsentMsgId ;
        getActivity().getMessageEditor().startEditingMessage(MessageEditorData.load(unsentMsgId));
        ActivityTestHelper.waitViewVisible(method + "; " + step, editorView);
        TestSuite.waitForIdleSync(this);

        step = "Saving previously unsent message " + unsentMsgId + " as a draft";
        helper.clickMenuItem(method + "; " + step, R.id.saveDraftButton);
        ActivityTestHelper.waitViewInvisible(method + "; " + step, editorView);

        assertEquals(method + "; " + step, DownloadStatus.DRAFT, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, unsentMsgId)));

        MyLog.v(this, method + " ended");
    }

}
