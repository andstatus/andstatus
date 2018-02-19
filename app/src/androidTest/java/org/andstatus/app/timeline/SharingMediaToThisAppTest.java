package org.andstatus.app.timeline;

import android.content.Intent;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.test.espresso.action.TypeTextAction;
import android.view.View;
import android.widget.TextView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.util.MyLog;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharingMediaToThisAppTest extends TimelineActivityTest<ActivityViewItem> {
    private MyServiceTestHelper mService;
    private MyAccount ma;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        mService = new MyServiceTestHelper();
        mService.setUp(demoData.gnusocialTestAccountName);
        ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        assertTrue(ma.isValid());
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        Uri mediaUri = demoData.localImageTestUri2;
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
        listActivityTestHelper.selectIdFromSelectorDialog(method, ma.getActorId());

        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewVisible(method, editorView);
        TextView details = editorView.findViewById(R.id.noteEditDetails);
        String textToFind = MyContextHolder.get().context().getText(R.string.label_with_media).toString();
        ActivityTestHelper.waitTextInAView(method, details, textToFind);

        String body = "Test note with a shared image " + demoData.testRunUid;
        TestSuite.waitForIdleSync();
        onView(withId(R.id.noteBodyEditText)).perform(new TypeTextAction(body));
        TestSuite.waitForIdleSync();

        mService.serviceStopped = false;
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
        helper.clickMenuItem(method, R.id.noteSendButton);
        ActivityTestHelper.waitViewInvisible(method, editorView);

        mService.waitForServiceStopped(false);

        String message = "Data was posted " + mService.getHttp().getPostedCounter() + " times; "
                + Arrays.toString(mService.getHttp().getResults().toArray());
        MyLog.v(this, method + "; " + message);
        assertTrue(message, mService.getHttp().getPostedCounter() > 0);
        assertTrue(message, mService.getHttp().substring2PostedPath("statuses/update").length() > 0);

        String condition = "BODY='" + body + "'";
        long unsentMsgId = MyQuery.conditionToLongColumnValue(NoteTable.TABLE_NAME, BaseColumns._ID, condition);
        assertTrue("Unsent note found: " + condition, unsentMsgId != 0);
        assertEquals("Status of unsent note", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, unsentMsgId)));

        DownloadData dd = DownloadData.getSingleForNote(unsentMsgId,
                MyContentType.IMAGE, null);
        MyLog.v(this, method + "; " + dd);
        assertEquals("Image URI stored", demoData.localImageTestUri2, dd.getUri());
        assertEquals("Loaded '" + dd.getUri() + "'; " + dd, DownloadStatus.LOADED, dd.getStatus());
    }
}
