package org.andstatus.app.msg;

import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharingMediaToThisAppTest extends TimelineActivityTest {
    private MyServiceTestHelper mService;
    private MyAccount ma;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        mService = new MyServiceTestHelper();
        mService.setUp(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        Uri mediaUri = DemoData.LOCAL_IMAGE_TEST_URI2;
        assertTrue(mediaUri != null);
        MyLog.i(this, "setUp ended");
        intent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        return intent;
    }

    @After
    public void tearDown() throws Exception {
        mService.tearDown();
    }

    @Test
    public void testSharingMediaToThisApp() throws InterruptedException {
        final String method = "testSharingMediaToThisApp";
        ListActivityTestHelper<TimelineActivity> listActivityTestHelper =
                ListActivityTestHelper.newForSelectorDialog(getActivity(), AccountSelector.getDialogTag());
        listActivityTestHelper.selectIdFromSelectorDialog(method, ma.getUserId());

        View editorView = getActivity().findViewById(R.id.message_editor);
        ActivityTestHelper.waitViewVisible(method, editorView);
        TextView details = (TextView) editorView.findViewById(R.id.messageEditDetails);
        String textToFind = MyContextHolder.get().context().getText(R.string.label_with_media).toString();
        ActivityTestHelper.waitTextInAView(method, details, textToFind);

        String body = "Test message with a shared image " + DemoData.TESTRUN_UID;
        EditText editText = (EditText) editorView.findViewById(R.id.messageBodyEditText);
        editText.requestFocus();
        TestSuite.waitForIdleSync();
        getInstrumentation().sendStringSync(body);
        TestSuite.waitForIdleSync();

        mService.serviceStopped = false;
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<TimelineActivity>(getActivity());
        helper.clickMenuItem(method, R.id.messageSendButton);
        ActivityTestHelper.waitViewInvisible(method, editorView);

        mService.waitForServiceStopped(false);

        String message = "Data was posted " + mService.httpConnectionMock.getPostedCounter() + " times; "
                + Arrays.toString(mService.httpConnectionMock.getResults().toArray());
        MyLog.v(this, method + "; " + message);
        assertTrue(message, mService.httpConnectionMock.getPostedCounter() > 0);
        assertTrue(message, mService.httpConnectionMock.substring2PostedPath("statuses/update").length() > 0);

        String condition = "BODY='" + body + "'";
        long unsentMsgId = MyQuery.conditionToLongColumnValue(MsgTable.TABLE_NAME, BaseColumns._ID, condition);
        assertTrue("Unsent message found: " + condition, unsentMsgId != 0);
        assertEquals("Status of unsent message", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, unsentMsgId)));

        DownloadData dd = DownloadData.getSingleForMessage(unsentMsgId,
                MyContentType.IMAGE, null);
        MyLog.v(this, method + "; " + dd);
        assertEquals("Image URI stored", DemoData.LOCAL_IMAGE_TEST_URI2, dd.getUri());
        assertEquals("Loaded '" + dd.getUri() + "'; " + dd, DownloadStatus.LOADED, dd.getStatus());
    }
}
