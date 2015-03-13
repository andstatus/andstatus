/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class MessageEditorTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity mActivity;

    private static MessageEditorData data = null;
    private static int editingStep = 0;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        MyLog.setLogToFile(true);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent();
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key, TimelineTypeEnum.HOME.save());
        // In order to shorten opening of activity in a case of a large database
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, false);
        setActivityIntent(intent);
        
        mActivity = getActivity();

        if (data == null) {
            data = getStaticData();
        }
        
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        MyLog.i(this, "setUp ended");
    }

    private MessageEditorData getStaticData() {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromUserId(mActivity.getCurrentMyAccountUserId());
        MessageEditorData data = new MessageEditorData(ma)
                .setMediaUri(Uri.parse("http://example.com/" + TestSuite.TESTRUN_UID + "/some.png"))
                .setInReplyToId(
                        MyProvider.oidToId(OidEnum.MSG_OID, MyContextHolder.get()
                                .persistentOrigins()
                                .fromName(TestSuite.CONVERSATION_ORIGIN_NAME).getId(),
                                TestSuite.CONVERSATION_ENTRY_MESSAGE_OID))
                .setRecipientId(
                        MyProvider.oidToId(OidEnum.USER_OID, ma.getOrigin().getId(),
                                TestSuite.CONVERSATION_MEMBER_USER_OID))
                .addMentionsToText()
                .setMessageText("Some text " + TestSuite.TESTRUN_UID);
        return data;
    }

    @Override
    protected void tearDown() throws Exception {
        MyLog.setLogToFile(false);
        super.tearDown();
    }

    public MessageEditorTest() {
        super(TimelineActivity.class);
    }

    public void testEditing1() throws InterruptedException {
        editingTester();
    }

    public void testEditing2() throws InterruptedException {
        editingTester();
    }
    
    private void editingTester() throws InterruptedException {
        editingStep++;
        TestSuite.waitForListLoaded(this, mActivity, 2);
        openEditor();
        switch (editingStep) {
            case 1:
                editingStep1();
                break;
            default :
                editingStep2();
                editingStep = 0;
                break;
        }
    }

    private void openEditor() throws InterruptedException {
        final String method = "openEditor";
        MenuItem createMessageButton = mActivity.getOptionsMenu().findItem(R.id.createMessageButton);
        assertTrue(createMessageButton != null);
        View editorView = mActivity.findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        if (editorView.getVisibility() != android.view.View.VISIBLE) {
            assertTrue("Blog button is visible", createMessageButton.isVisible());
            ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, mActivity);
            helper.clickMenuItem(method + " opening editor", R.id.createMessageButton);
        }
        assertEquals("Editor appeared", android.view.View.VISIBLE, editorView.getVisibility());
    }
    
    private void editingStep1() throws InterruptedException {
        final String method = "editingStep1";

        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, mActivity);
        helper.clickMenuItem(method + " hiding editor", R.id.hideMessageButton);
        View editorView = mActivity.findViewById(R.id.message_editor);
        assertFalse("Editor hidden again", editorView.getVisibility() == android.view.View.VISIBLE);

        final MessageEditor editor = mActivity.getMessageEditor();
        Runnable startEditing = new Runnable() {
            @Override
            public void run() {
                editor.clearEditor();
                editor.startEditingMessage(data);
            }
          };
        getInstrumentation().runOnMainSync(startEditing);
        getInstrumentation().waitForIdleSync();
        
        assertTrue(editorView.getVisibility() == android.view.View.VISIBLE);

        assertInitialText("Initial text");
    }

    private void editingStep2() throws InterruptedException {
        final String method = "editingStep2";
        assertInitialText("Message restored");
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, mActivity);
        View editorView = mActivity.findViewById(R.id.message_editor);
        helper.clickMenuItem(method + "; Create message cannot hide editor", R.id.createMessageButton);
        assertTrue("Create message cannot hide editor", editorView.getVisibility() == android.view.View.VISIBLE);
        helper.clickMenuItem(method + " hide editor", R.id.hideMessageButton);
        assertFalse("Editor hidden again", editorView.getVisibility() == android.view.View.VISIBLE);
        helper.clickMenuItem(method + " clicker 5", R.id.createMessageButton);
        assertTrue("Editor appeared", editorView.getVisibility() == android.view.View.VISIBLE);
        assertTextCleared();
    }

    private void assertInitialText(final String description) {
        final MessageEditor editor = mActivity.getMessageEditor();
        Runnable assertEditor = new Runnable() {
            @Override
            public void run() {
                assertEquals(description, data, editor.getData());
            }
          };
        getInstrumentation().runOnMainSync(assertEditor);
    }
    
    private void assertTextCleared() {
        final MessageEditor editor = mActivity.getMessageEditor();
        assertTrue("Editor is not null", editor != null);
        Runnable assertEditor = new Runnable() {
            @Override
            public void run() {
                assertEquals(new MessageEditorData(
                        MyContextHolder.get().persistentAccounts().fromUserId(
                        mActivity.getCurrentMyAccountUserId())), editor.getData());
            }
          };
        getInstrumentation().runOnMainSync(assertEditor);
    }
    
    public void testContextMenu() throws InterruptedException {
        final String method = "testContextMenu";
        TestSuite.waitForListLoaded(this, mActivity, 2);
        openEditor();
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, ConversationActivity.class); 
        int position = helper.getPositionOfReply();
        long msgId = helper.getItemIdAtPosition(position);
        String body = MyProvider.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId);

        helper.invokeContextMenuAction(method, position, ContextMenuItem.COPY_TEXT);
        assertEquals(body, getClipboardText());
        
        helper.invokeContextMenuAction(method, position, ContextMenuItem.COPY_AUTHOR);
        String text = getClipboardText();
        assertTrue("Text: '" + text + "'", text.startsWith("@") && text.lastIndexOf("@") > 1);
    }
    
    private String getClipboardText() {
        final String method = "getClipboardText";
        MyLog.v(MessageEditorTest.this, method + " started");
        ClipboardReader reader = new ClipboardReader();
        getInstrumentation().runOnMainSync(reader);
        MyLog.v(MessageEditorTest.this, method + "; clip='" + reader.clip + "'");
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
