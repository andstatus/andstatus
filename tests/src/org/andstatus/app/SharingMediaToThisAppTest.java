package org.andstatus.app;

import android.content.Intent;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;

import java.util.Arrays;

public class SharingMediaToThisAppTest extends ActivityInstrumentationTestCase2<TimelineActivity> {
    MyServiceTestHelper mService = new MyServiceTestHelper();
    MyAccount ma;

    public SharingMediaToThisAppTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);

        mService.setUp();
        
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME);
        assertTrue(ma != null);
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        Uri mediaUri = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon.png");
        assertTrue(mediaUri != null);
        intent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        setActivityIntent(intent);
        
    }
    
    public void testSharingMediaToThisApp() throws InterruptedException {
        final String method = "testSharingMediaToThisApp";
        ListActivityTestHelper<TimelineActivity> helperTimelineActivity = new ListActivityTestHelper<TimelineActivity>(this, AccountSelector.class);
        AccountSelector selector = (AccountSelector) helperTimelineActivity.waitForNextActivity(method, 15000);
        ListActivityTestHelper<AccountSelector> helperAccountSelector = new ListActivityTestHelper<AccountSelector>(this, selector);
        int position = helperAccountSelector.getPositionOfItemId(ma.getUserId());
        assertTrue("Account found", position >= 0);
        helperAccountSelector.selectListPosition(method, position);
        helperAccountSelector.clickListPosition(method, position);
        
        View editorView = getActivity().findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        assertTrue("Editor appeared", editorView.getVisibility() == android.view.View.VISIBLE);
        TextView details = (TextView) editorView.findViewById(R.id.messageEditDetails);
        String textToFind = MyContextHolder.get().context().getText(R.string.label_with_media).toString();
        assertTrue("Found text '" + textToFind + "'", details.getText().toString().contains(textToFind));

        EditText editText = (EditText) editorView.findViewById(R.id.messageBodyEditText);
        editText.requestFocus();
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync("Test message with an attached image");
        helperTimelineActivity.clickButton(method, R.id.messageEditSendButton);

        mService.waitForServiceStopped();
        
        String message = "Data was posted " + mService.httpConnectionMock.getPostedCounter() + " times; "
                + Arrays.toString(mService.httpConnectionMock.getPostedPaths().toArray(new String[]{}));
        MyLog.v(this, method  + "; " + message);
        assertTrue(message, mService.httpConnectionMock.getPostedCounter() > 0);
        assertTrue(message, mService.httpConnectionMock.substring2PostedPath("statuses/update").length() > 0 );
    }
    
    @Override
    protected void tearDown() throws Exception {
        mService.tearDown();
        super.tearDown();
    }
}
