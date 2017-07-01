package org.andstatus.app.msg;

import android.content.Intent;
import android.provider.BaseColumns;
import android.support.test.espresso.action.TypeTextAction;
import android.view.View;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.service.MyServiceTestHelper;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnsentMessagesTest extends TimelineActivityTest {
    private final MyServiceTestHelper mService = new MyServiceTestHelper();

    @Override
    protected Intent getActivityIntent() {
        TestSuite.initializeWithData(this);

        mService.setUp(null);
        MyAccount ma = DemoData.getMyAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        return new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(Timeline.getTimeline(TimelineType.EVERYTHING, ma, 0, ma.getOrigin())));
    }

    @After
    public void tearDown() throws Exception {
        mService.tearDown();
    }

    @Test
    public void testEditUnsentMessage() throws InterruptedException {
        final String method = "testEditUnsentMessage";
        String step = "Start editing a message";
        MyLog.v(this, method + " started");
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
        View editorView = getActivity().findViewById(R.id.message_editor);
        helper.clickMenuItem(method + "; " + step, R.id.createMessageButton);
        ActivityTestHelper.waitViewVisible(method + "; " + step, editorView);

        String body = "Test unsent message, which we will try to edit " + DemoData.TESTRUN_UID;
        TestSuite.waitForIdleSync();
        onView(withId(R.id.messageBodyEditText)).perform(new TypeTextAction(body));
        TestSuite.waitForIdleSync();

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
        TestSuite.waitForIdleSync();

        step = "Saving previously unsent message " + unsentMsgId + " as a draft";
        helper.clickMenuItem(method + "; " + step, R.id.saveDraftButton);
        ActivityTestHelper.waitViewInvisible(method + "; " + step, editorView);

        assertEquals(method + "; " + step, DownloadStatus.DRAFT, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, unsentMsgId)));

        MyLog.v(this, method + " ended");
    }

    @Test
    public void testGnuSocialReblog() throws InterruptedException {
        final String method = "testGnuSocialReblog";
        MyLog.v(this, method + " started");
        TestSuite.waitForListLoaded(getActivity(), 1);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity());
        long msgId = helper.getListItemIdOfLoadedReply();
        String msgOid = MyQuery.idToOid(OidEnum.MSG_OID, msgId, 0);
        String logMsg = MyQuery.msgInfoForLog(msgId);
        assertTrue(logMsg, helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.REBLOG));
        mService.serviceStopped = false;
        TestSuite.waitForIdleSync();
        mService.waitForServiceStopped(false);

        List<HttpReadResult> results = mService.getHttp().getResults();
        assertTrue("No results in " + mService.getHttp().toString(), !results.isEmpty());
        String urlFound = "";
        for (HttpReadResult result : results) {
            if (result.getUrl().contains("retweet")) {
                urlFound = result.getUrl();
                if (result.getUrl().contains(msgOid)) {
                    break;
                }
            }
        }
        assertTrue("URL '" + urlFound + "' doesn't contain message oid " + logMsg, urlFound.contains(msgOid));

        MyLog.v(this, method + " ended");
    }

}
