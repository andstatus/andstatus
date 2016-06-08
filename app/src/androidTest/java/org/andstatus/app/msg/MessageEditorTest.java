/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.msg;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class MessageEditorTest extends ActivityInstrumentationTestCase2<TimelineActivity> {
    private MessageEditorData data = null;
    private static int editingStep = 0;

    public MessageEditorTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        MyLog.setLogToFile(true);

        if (editingStep == 0) {
            SharedPreferencesUtil.putLong(MyPreferences.KEY_BEING_EDITED_MESSAGE_ID, 0);
        }

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(ma.getUserId(), TimelineType.HOME, false, 0));
        setActivityIntent(intent);

        data = getStaticData();

        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        MyLog.i(this, "setUp ended");
    }

    private MessageEditorData getStaticData() {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromUserId(getActivity().getCurrentMyAccountUserId());
        return MessageEditorData.newEmpty(ma)
                .setInReplyToId(
                        MyQuery.oidToId(OidEnum.MSG_OID, MyContextHolder.get()
                                        .persistentOrigins()
                                        .fromName(TestSuite.CONVERSATION_ORIGIN_NAME).getId(),
                                TestSuite.CONVERSATION_ENTRY_MESSAGE_OID))
                .setRecipientId(
                        MyQuery.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                                TestSuite.CONVERSATION_MEMBER_USER_OID))
                .addMentionsToText()
                .setBody("Some static text " + TestSuite.TESTRUN_UID);
    }

    @Override
    protected void tearDown() throws Exception {
        MyLog.setLogToFile(false);
        super.tearDown();
    }

    public void testEditing1() throws InterruptedException {
        editingTester();
    }

    public void testEditing2() throws InterruptedException {
        editingTester();
    }

    private void editingTester() throws InterruptedException {
        editingStep++;
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        switch (editingStep) {
            case 1:
                openEditor();
                editingStep1();
                break;
            default:
                editingStep2();
                editingStep = 0;
                break;
        }
        MyLog.v(this, "After step " + editingStep + " ended");
    }

    private void openEditor() throws InterruptedException {
        final String method = "openEditor";
        MenuItem createMessageButton = getActivity().getOptionsMenu().findItem(R.id.createMessageButton);
        assertTrue(createMessageButton != null);
        View editorView = getActivity().findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        if (editorView.getVisibility() != android.view.View.VISIBLE) {
            assertTrue("Blog button is visible", createMessageButton.isVisible());
            ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(this, getActivity());
            helper.clickMenuItem(method + " opening editor", R.id.createMessageButton);
        }
        assertEquals("Editor appeared", android.view.View.VISIBLE, editorView.getVisibility());
    }

    private void editingStep1() throws InterruptedException {
        final String method = "editingStep1";
        MyLog.v(this, method + " started");

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(this, getActivity());
        helper.clickMenuItem(method + " hiding editor", R.id.saveDraftButton);
        View editorView = getActivity().findViewById(R.id.message_editor);
        ActivityTestHelper.waitViewInvisible(method, editorView);

        final MessageEditor editor = getActivity().getMessageEditor();
        Runnable startEditing = new Runnable() {
            @Override
            public void run() {
                editor.startEditingMessage(data);
            }
        };
        getInstrumentation().runOnMainSync(startEditing);
        getInstrumentation().waitForIdleSync();

        ActivityTestHelper.waitViewVisible(method, editorView);

        assertInitialText("Initial text");
        MyLog.v(this, method + " ended");
    }

    private void editingStep2() throws InterruptedException {
        final String method = "editingStep2";
        MyLog.v(this, method + " started");
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(this, getActivity());
        View editorView = getActivity().findViewById(R.id.message_editor);
        ActivityTestHelper.waitViewVisible(method + "; Restored message is visible", editorView);
        assertInitialText("Message restored");
        helper.clickMenuItem(method + " hide editor", R.id.saveDraftButton);
        ActivityTestHelper.waitViewInvisible(method + "; Editor is hidden again", editorView);
        helper.clickMenuItem(method + " clicker 5", R.id.createMessageButton);
        ActivityTestHelper.waitViewVisible(method + "; Editor appeared", editorView);
        assertTextCleared();
        helper.clickMenuItem(method + " click Discard", R.id.discardButton);
        ActivityTestHelper.waitViewInvisible(method + "; Editor hidden after discard", editorView);
        MyLog.v(this, method + " ended");
    }

    public void testAttachImage() throws InterruptedException {
        final String method = "testAttachImage";
        MyLog.v(this, method + " started");

        View editorView = getActivity().findViewById(R.id.message_editor);
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(this, getActivity());
        helper.clickMenuItem(method + " clicker createMessageButton", R.id.createMessageButton);
        ActivityTestHelper.waitViewVisible(method + "; Editor appeared", editorView);
        assertTextCleared();

        String body = "Message with attachment " + TestSuite.TESTRUN_UID;
        EditText editText = (EditText) editorView.findViewById(R.id.messageBodyEditText);
        editText.requestFocus();
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync(body);
        TestSuite.waitForIdleSync(this);

        getActivity().selectorActivityMock = helper;
        helper.clickMenuItem(method + " clicker attach_menu_id", R.id.attach_menu_id);
        assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id));
        getActivity().selectorActivityMock = null;

        Instrumentation.ActivityMonitor activityMonitor = getInstrumentation().addMonitor(HelpActivity.class.getName(), null, false);

        Intent intent = new Intent(getActivity(), HelpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().getApplicationContext().startActivity(intent);

        Activity selectorActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        assertTrue(selectorActivity != null);
        ActivityTestHelper.waitViewInvisible(method, editorView);
        Thread.sleep(4000);
        selectorActivity.finish();

        MyLog.i(method, "Callback from a selector");
        Intent data = new Intent();
        data.setData(TestSuite.LOCAL_IMAGE_TEST_URI2);
        getActivity().onActivityResult(ActivityRequestCode.ATTACH.id, Activity.RESULT_OK, data);

        final MessageEditor editor = getActivity().getMessageEditor();
        for (int attempt=0; attempt < 4; attempt++) {
            ActivityTestHelper.waitViewVisible(method, editorView);
            // Due to a race the editor may open before this change first.
            if (TestSuite.LOCAL_IMAGE_TEST_URI2.equals(editor.getData().getMediaUri())) {
                break;
            }
            Thread.sleep(2000);
        }
        assertEquals("Image attached", TestSuite.LOCAL_IMAGE_TEST_URI2, editor.getData().getMediaUri());
        assertEquals("Text is the same", body, editText.getText().toString().trim());

        MyLog.v(this, method + " ended");
    }

    private void assertInitialText(final String description) throws InterruptedException {
        final MessageEditor editor = getActivity().getMessageEditor();
        TextView textView = (TextView) getActivity().findViewById(R.id.messageBodyEditText);
        ActivityTestHelper.waitTextInAView(description, textView, data.body);
        MyLog.v(this, description + " text:'" + editor.getData().body + "'");
        assertEquals(description, data, editor.getData());
    }

    private void assertTextCleared() {
        final MessageEditor editor = getActivity().getMessageEditor();
        assertTrue("Editor is not null", editor != null);
        assertEquals(MessageEditorData.newEmpty(
                MyContextHolder.get().persistentAccounts().fromUserId(
                        getActivity().getCurrentMyAccountUserId())), editor.getData());
    }

    public void testContextMenuWhileEditing() throws InterruptedException {
        final String method = "testContextMenuWhileEditing";
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        openEditor();
        ListActivityTestHelper<TimelineActivity> helper =
                new ListActivityTestHelper<>(this, ConversationActivity.class);
        long msgId = helper.getListItemIdOfLoadedReply();
        String logMsg = "msgId=" + msgId;

        String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.COPY_TEXT);
        assertEquals(logMsg, body, getClipboardText(method));

        helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.COPY_AUTHOR);
        String text = getClipboardText(method);
        assertTrue(logMsg + "; Text: '" + text + "'", text.startsWith("@") && text.lastIndexOf("@") > 1);
    }

    private String getClipboardText(String methodExt) throws InterruptedException {
        final String method = "getClipboardText";
        MyLog.v(methodExt, method + " started");
        TestSuite.waitForIdleSync(this);
        ClipboardReader reader = new ClipboardReader();
        getInstrumentation().runOnMainSync(reader);
        MyLog.v(methodExt, method + "; clip='" + reader.clip + "'");
        if (reader.clip == null) {
            return "";
        }
        ClipData.Item item = reader.clip.getItemAt(0);
        return (TextUtils.isEmpty(item.getHtmlText()) ? item.getText() : item.getHtmlText())
                .toString();
    }

    private static class ClipboardReader implements Runnable {
        volatile ClipData clip = null;

        @Override
        public void run() {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            clip = clipboard.getPrimaryClip();
        }
    }

}
