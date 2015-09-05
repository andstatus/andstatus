package org.andstatus.app.msg;

import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;

public class SharingMediaToThisAppTest extends ActivityInstrumentationTestCase2<TimelineActivity> {
    final MyServiceTestHelper mService = new MyServiceTestHelper();
    MyAccount ma;

    public SharingMediaToThisAppTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);

        mService.setUp();
        
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        Uri mediaUri = TestSuite.LOCAL_IMAGE_TEST_URI2;
        assertTrue(mediaUri != null);
        intent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        setActivityIntent(intent);
        
    }
    
    public void testSharingMediaToThisApp() throws InterruptedException {
        final String method = "testSharingMediaToThisApp";
        ListActivityTestHelper<TimelineActivity> helperTimelineActivity = new ListActivityTestHelper<>(this, AccountSelector.class);
        AccountSelector selector = (AccountSelector) helperTimelineActivity.waitForNextActivity(method, 15000);
        ListActivityTestHelper<AccountSelector> helperAccountSelector = new ListActivityTestHelper<>(this, selector);
        int position = helperAccountSelector.getPositionOfListItemId(ma.getUserId());
        assertTrue("Account found", position >= 0);
        helperAccountSelector.selectListPosition(method, position);
        helperAccountSelector.clickListAtPosition(method, position);
        
        View editorView = getActivity().findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        assertTrue("Editor appeared", editorView.getVisibility() == android.view.View.VISIBLE);
        TextView details = (TextView) editorView.findViewById(R.id.messageEditDetails);
        String textToFind = MyContextHolder.get().context().getText(R.string.label_with_media).toString();
        assertTrue("Found text '" + textToFind + "'", details.getText().toString().contains(textToFind));

        final String body = "Test message with a shared image " + TestSuite.TESTRUN_UID;
        EditText editText = (EditText) editorView.findViewById(R.id.messageBodyEditText);
        editText.requestFocus();
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync(body);
        helperTimelineActivity.clickView(method, R.id.messageEditSendButton);
        mService.serviceStopped = false;
        mService.waitForServiceStopped();
        
        String message = "Data was posted " + mService.httpConnectionMock.getPostedCounter() + " times; "
                + Arrays.toString(mService.httpConnectionMock.getResults().toArray());
        MyLog.v(this, method + "; " + message);
        assertTrue(message, mService.httpConnectionMock.getPostedCounter() > 0);
        assertTrue(message, mService.httpConnectionMock.substring2PostedPath("statuses/update").length() > 0 );

        String condition = "BODY='" + body + "'";
        long unsentMsgId = MyQuery.conditionToLongColumnValue(MyDatabase.Msg.TABLE_NAME, BaseColumns._ID, condition);
        assertTrue("Unsent message found: " + condition, unsentMsgId != 0);
        assertEquals("Status of unsent message", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, unsentMsgId)));

        DownloadData dd = DownloadData.getSingleForMessage(unsentMsgId,
                MyContentType.IMAGE, null);
        MyLog.v(this, method + "; " + dd);
        assertEquals("Image URI stored", TestSuite.LOCAL_IMAGE_TEST_URI2, dd.getUri());
        assertEquals("Loaded '" + dd.getUri() + "'; " + dd, DownloadStatus.LOADED, dd.getStatus());

    }
    
    @Override
    protected void tearDown() throws Exception {
        mService.tearDown();
        super.tearDown();
    }
}
