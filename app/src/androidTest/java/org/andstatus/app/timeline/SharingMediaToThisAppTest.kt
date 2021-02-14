package org.andstatus.app.timeline;

import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.TextView;

import androidx.test.espresso.action.ReplaceTextAction;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SharingMediaToThisAppTest extends TimelineActivityTest<ActivityViewItem> {
    private MyServiceTestHelper mService;
    private MyAccount ma;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);

        mService = new MyServiceTestHelper();
        mService.setUp(demoData.gnusocialTestAccountName);
        ma = demoData.getGnuSocialAccount();
        assertTrue(ma.isValid());
        myContextHolder.getBlocking().accounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        Uri mediaUri = demoData.localImageTestUri2;
        assertNotNull(mediaUri);
        intent.putExtra(Intent.EXTRA_STREAM, mediaUri);
        MyLog.i(this, "setUp ended");
        return intent;
    }

    @After
    public void tearDown() throws Exception {
        mService.tearDown();
    }

    @Test
    public void testSharingMediaToThisApp() throws InterruptedException {
        final String method = "testSharingMediaToThisApp";
        ListScreenTestHelper<TimelineActivity> listScreenTestHelper =
                ListScreenTestHelper.newForSelectorDialog(getActivity(), AccountSelector.getDialogTag());
        listScreenTestHelper.selectIdFromSelectorDialog(method, ma.getActorId());

        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewVisible(method, editorView);
        TextView details = editorView.findViewById(R.id.noteEditDetails);
        String textToFind = myContextHolder.getNow().context().getText(R.string.label_with_media).toString();
        ActivityTestHelper.waitTextInAView(method, details, textToFind);

        TestSuite.waitForIdleSync();
        final String content = "Test note with a shared image " + demoData.testRunUid;
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(content));
        TestSuite.waitForIdleSync();

        mService.serviceStopped = false;
        ActivityTestHelper.clickSendButton(method, getActivity());
        mService.waitForServiceStopped(false);

        String message = "Data was posted " + mService.getHttp().getPostedCounter() + " times; "
                + Arrays.toString(mService.getHttp().getResults().toArray());
        MyLog.v(this, method + "; " + message);
        assertTrue(message, mService.getHttp().getPostedCounter() > 0);
        assertTrue(message, mService.getHttp().substring2PostedPath("statuses/update").length() > 0);

        String condition = NoteTable.CONTENT + "='" + content + "'";
        long unsentMsgId = MyQuery.conditionToLongColumnValue(NoteTable.TABLE_NAME, BaseColumns._ID, condition);
        assertTrue("Unsent note found: " + condition, unsentMsgId != 0);
        assertEquals("Status of unsent note", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)));

        DownloadData dd = DownloadData.getSingleAttachment(unsentMsgId);
        MyLog.v(this, method + "; " + dd);
        assertEquals("Image URI stored", demoData.localImageTestUri2, dd.getUri());
        assertEquals("Loaded '" + dd.getUri() + "'; " + dd, DownloadStatus.LOADED, dd.getStatus());
    }
}
