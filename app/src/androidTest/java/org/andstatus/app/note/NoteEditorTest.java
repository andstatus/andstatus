/*
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

package org.andstatus.app.note;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.espresso.action.ReplaceTextAction;
import androidx.test.espresso.action.TypeTextAction;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.ListActivityTestHelper;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyHtmlTest;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.hamcrest.CoreMatchers;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class NoteEditorTest extends TimelineActivityTest<ActivityViewItem> {
    private NoteEditorData data = null;
    private static final AtomicInteger editingStep = new AtomicInteger();

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        if (editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0);
        }

        final MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        myContextHolder.getNow().accounts().setCurrentAccount(ma);

        data = getStaticData(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                myContextHolder.getNow().timelines().get(TimelineType.HOME, ma.getActor(), Origin.EMPTY).getUri());
    }

    private NoteEditorData getStaticData(MyAccount ma) {
        return NoteEditorData.newReplyTo(MyQuery.oidToId(OidEnum.NOTE_OID, ma.getOrigin().getId(),
                        demoData.conversationEntryNoteOid), ma)
                .addToAudience(MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOrigin().getId(),
                        demoData.conversationEntryAuthorOid))
                .addMentionsToText()
                .setContent(MyHtmlTest.twitterBodyTypedPlain + " " + demoData.testRunUid, TextMediaType.PLAIN);
    }

    @Test
    public void testEditing1() throws InterruptedException {
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        editingTester();
    }

    @Test
    public void testEditing2() throws InterruptedException {
        editingTester();
    }

    private void editingTester() throws InterruptedException {
        TestSuite.waitForListLoaded(getActivity(), 2);
        switch (editingStep.incrementAndGet()) {
            case 2:
                editingStep2();
                break;
            default:
                editingStep.set(1);
                ActivityTestHelper.openEditor("default", getActivity());
                editingStep1();
                break;
        }
        MyLog.v(this, "After step " + editingStep + " ended");
    }

    private void editingStep1() throws InterruptedException {
        final String method = "editingStep1";
        MyLog.v(this, method + " started");

        View editorView = ActivityTestHelper.hideEditorAndSaveDraft(method, getActivity());

        final NoteEditor editor = getActivity().getNoteEditor();
        getInstrumentation().runOnMainSync(() -> editor.startEditingNote(data));
        TestSuite.waitForIdleSync();

        ActivityTestHelper.waitViewVisible(method, editorView);

        assertInitialText("Initial text");
        MyLog.v(this, method + " ended");
    }

    private void editingStep2() throws InterruptedException {
        final String method = "editingStep2";
        MyLog.v(this, method + " started");
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewVisible(method + "; Restored note is visible", editorView);
        assertInitialText("Note restored");
        ActivityTestHelper.hideEditorAndSaveDraft(method, getActivity());
        ActivityTestHelper.openEditor(method, getActivity());
        assertTextCleared(this);
        helper.clickMenuItem(method + " click Discard", R.id.discardButton);
        ActivityTestHelper.waitViewInvisible(method + "; Editor hidden after discard", editorView);
        MyLog.v(this, method + " ended");
    }

    @Test
    public void attachOneImage() throws InterruptedException {
        attachImages(this, 1, 1);
    }

    @Test
    public void attachTwoImages() throws InterruptedException {
        attachImages(this,2, 1);
    }

    static void attachImages(TimelineActivityTest<ActivityViewItem> test, int toAdd, int toExpect) throws InterruptedException {
        final String method = "attachImages" + toAdd;
        MyLog.v(test, method + " started");

        ActivityTestHelper.hideEditorAndSaveDraft(method, test.getActivity());
        View editorView = ActivityTestHelper.openEditor(method, test.getActivity());
        assertTextCleared(test);

        TestSuite.waitForIdleSync();
        final String noteName = "A note " + toAdd + " " + test.getClass().getSimpleName() + " can have a title (name)";
        final String content = "Note with " + toExpect + " attachment" +
                (toExpect == 1 ? "" : "s") + " " +
                demoData.testRunUid;
        onView(withId(R.id.note_name_edit)).perform(new TypeTextAction(noteName));
        onView(withId(R.id.noteBodyEditText)).perform(new TypeTextAction(content));

        attachImage(test, editorView, demoData.localImageTestUri2);
        if (toAdd == 2) {
            attachImage(test, editorView, demoData.localImageTestUri);
        }
        final NoteEditor editor = test.getActivity().getNoteEditor();
        assertEquals("All image attached " + editor.getData().getAttachedImageFiles(), toExpect,
                editor.getData().getAttachedImageFiles().list.size());

        onView(withId(R.id.noteBodyEditText)).check(matches(withText(content + " ")));
        onView(withId(R.id.note_name_edit)).check(matches(withText(noteName)));

        ActivityTestHelper.hideEditorAndSaveDraft(method, test.getActivity());

        MyLog.v(test, method + " ended");
    }

    private static void attachImage(TimelineActivityTest<ActivityViewItem> test, View editorView, Uri imageUri) throws InterruptedException {
        final String method = "attachImage";
        TestSuite.waitForIdleSync();

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(test.getActivity());
        test.getActivity().setSelectorActivityMock(helper);
        helper.clickMenuItem(method + " clicker attach_menu_id", R.id.attach_menu_id);
        assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id));
        test.getActivity().setSelectorActivityMock(null);

        Instrumentation.ActivityMonitor activityMonitor = test.getInstrumentation()
                .addMonitor(HelpActivity.class.getName(), null, false);

        Intent intent1 = new Intent(test.getActivity(), HelpActivity.class);
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        test.getActivity().getApplicationContext().startActivity(intent1);

        Activity selectorActivity = test.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 25000);
        assertTrue(selectorActivity != null);
        ActivityTestHelper.waitViewInvisible(method, editorView);
        DbUtils.waitMs(method, 10000);
        selectorActivity.finish();

        MyLog.i(method, "Callback from a selector");
        Intent intent2 = new Intent();
        intent2.setDataAndType(imageUri, MyContentType.IMAGE.generalMimeType);
        test.getActivity().runOnUiThread(() -> {
            test.getActivity().onActivityResult(ActivityRequestCode.ATTACH.id, Activity.RESULT_OK, intent2);
        });

        final NoteEditor editor = test.getActivity().getNoteEditor();
        for (int attempt=0; attempt < 4; attempt++) {
            ActivityTestHelper.waitViewVisible(method, editorView);
            // Due to a race the editor may open before this change first.
            if (editor.getData().getAttachedImageFiles().forUri(imageUri).isPresent()) {
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        assertTrue("Image attached", editor.getData().getAttachedImageFiles()
                .forUri(imageUri).isPresent());
    }

    private void assertInitialText(final String description) throws InterruptedException {
        final NoteEditor editor = getActivity().getNoteEditor();
        TextView textView = getActivity().findViewById(R.id.noteBodyEditText);
        ActivityTestHelper.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN));
        assertEquals(description, data.toTestSummary(), editor.getData().toTestSummary());
    }

    private static void assertTextCleared(TimelineActivityTest<ActivityViewItem> test) {
        final NoteEditor editor = test.getActivity().getNoteEditor();
        assertTrue("Editor is not null", editor != null);
        assertEquals(NoteEditorData.newEmpty(
                test.getActivity().getMyContext().accounts().getCurrentAccount()).toTestSummary(),
                editor.getData().toTestSummary());
    }


    /* We see crash in the test...
        java.lang.IllegalStateException: beginBroadcast() called while already in a broadcast
        at android.os.RemoteCallbackList.beginBroadcast(RemoteCallbackList.java:241)
        at com.android.server.clipboard.ClipboardService.setPrimaryClipInternal(ClipboardService.java:583)

        So we split clipboard copying functions into two tests
        ...but this doesn't help...
    */
    @Ignore("We see crash in the test...")
    @Test
    public void testContextMenuWhileEditing1() throws InterruptedException {
        final String method = "testContextMenuWhileEditing";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ActivityTestHelper.openEditor(method, getActivity());
        ListActivityTestHelper<TimelineActivity> helper =
                new ListActivityTestHelper<>(getActivity(), ConversationActivity.class);
        long listItemId = helper.getListItemIdOfLoadedReply();
        String logMsg = "listItemId=" + listItemId;
        long noteId = TimelineType.HOME.showsActivities() ?
                MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId) : listItemId;
        logMsg += ", noteId=" + noteId;

        String content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId);
        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_TEXT, R.id.note_wrapper);
        assertEquals(logMsg, content, getClipboardText(method));
   }

    @Ignore("We see crash in the test...")
    @Test
    public void testContextMenuWhileEditing2() throws InterruptedException {
        final String method = "testContextMenuWhileEditing";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ActivityTestHelper.openEditor(method, getActivity());
        ListActivityTestHelper<TimelineActivity> helper =
                new ListActivityTestHelper<>(getActivity(), ConversationActivity.class);
        long listItemId = helper.getListItemIdOfLoadedReply();
        String logMsg = "listItemId=" + listItemId;

        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_AUTHOR, R.id.note_wrapper);
        String text = getClipboardText(method);
        assertThat(text, CoreMatchers.startsWith("@"));
        assertTrue(logMsg + "; Text: '" + text + "'", text.startsWith("@") && text.lastIndexOf("@") > 1);
    }

    private String getClipboardText(String methodExt) {
        final String method = "getClipboardText";
        try {
            MyLog.v(methodExt, method + " started");
            TestSuite.waitForIdleSync();
            ClipboardReader reader = new ClipboardReader();
            getInstrumentation().runOnMainSync(reader);
            MyLog.v(methodExt, method + "; clip='" + reader.clip + "'");
            if (reader.clip == null) {
                return "";
            }
            ClipData.Item item = reader.clip.getItemAt(0);
            String text = (StringUtil.isEmpty(item.getHtmlText()) ? item.getText() : item.getHtmlText())
                    .toString();
            MyLog.v(methodExt, method + " ended. Text: " + text);
            return text;
        } catch (Exception e) {
            MyLog.e(method, e);
            return "Exception: " + e.getMessage();
        }
    }

    private static class ClipboardReader implements Runnable {
        volatile ClipData clip = null;

        @Override
        public void run() {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) myContextHolder.getNow().context()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            clip = clipboard.getPrimaryClip();
        }
    }

    @Test
    public void editLoadedNote() throws InterruptedException {
        final String method = "editLoadedNote";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(),
                ConversationActivity.class);
        long listItemId = helper.findListItemId("My loaded note",
                item -> item.author.getActorId() == data.getMyAccount().getActorId()
                        && item.noteStatus == DownloadStatus.LOADED);

        long noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId);
        String logMsg = "itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'";

        boolean invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.EDIT, R.id.note_wrapper);
        logMsg += ";" + (invoked ? "" : " failed to invoke Edit menu item," );
        assertTrue(logMsg, invoked);
        ActivityTestHelper.closeContextMenu(getActivity());

        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewVisible(method + " " + logMsg, editorView);

        assertEquals("Loaded note should be in DRAFT state on Edit start: " + logMsg, DownloadStatus.DRAFT,
                getDownloadStatus(noteId));

        ActivityTestHelper<TimelineActivity> helper2 = new ActivityTestHelper<>(getActivity());
        helper2.clickMenuItem(method + " clicker Discard " + logMsg, R.id.discardButton);
        ActivityTestHelper.waitViewInvisible(method + " " + logMsg, editorView);

        assertEquals("Loaded note should be unchanged after Discard: " + logMsg, DownloadStatus.LOADED,
                waitForDownloadStatus(noteId, DownloadStatus.LOADED));
    }

    private DownloadStatus waitForDownloadStatus(long noteId, DownloadStatus expected) {
        DownloadStatus downloadStatus = DownloadStatus.UNKNOWN;
        for (int i = 0; i < 30; i++) {
            downloadStatus = getDownloadStatus(noteId);
            if (downloadStatus == expected) return downloadStatus;
            DbUtils.waitMs(this, 100);
        }
        return downloadStatus;
    }

    private DownloadStatus getDownloadStatus(long noteId) {
        return DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId));
    }

    @Test
    public void replying() throws InterruptedException {
        final String method = "replying";
        TestSuite.waitForListLoaded(getActivity(), 2);

        View editorView = ActivityTestHelper.hideEditorAndSaveDraft(method, getActivity());

        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(),
                ConversationActivity.class);
        ActivityViewItem viewItem = (ActivityViewItem) helper.findListItem("Some others loaded note",
                item -> item.author.getActorId() != data.getMyAccount().getActorId()
                        && item.noteStatus == DownloadStatus.LOADED);
        long listItemId = viewItem.getId();

        long noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId);
        String logMsg = "itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'";
        assertEquals(logMsg, viewItem.noteViewItem.getId(), noteId);

        boolean invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.REPLY, R.id.note_wrapper);
        logMsg += ";" + (invoked ? "" : " failed to invoke Reply menu item," );
        assertTrue(logMsg, invoked);
        ActivityTestHelper.closeContextMenu(getActivity());

        ActivityTestHelper.waitViewVisible(method + " " + logMsg, editorView);

        onView(withId(R.id.noteBodyEditText)).check(matches(withText(startsWith("@"))));

        TestSuite.waitForIdleSync();
        final String content = "Replying to you during " + demoData.testRunUid;
        EditText bodyText = editorView.findViewById(R.id.noteBodyEditText);
        // Espresso types in the centre, unfortunately, so we need to retype text
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(bodyText.getText().toString().trim()
                + " " + content));
        TestSuite.waitForIdleSync();

        ActivityTestHelper.hideEditorAndSaveDraft(method + " Save draft " + logMsg, getActivity());
        long draftNoteId = ActivityTestHelper.waitAndGetIdOfStoredNote(method + " " + logMsg, content);

        assertEquals("Saved note should be in DRAFT state: " + logMsg, DownloadStatus.DRAFT,
                getDownloadStatus(draftNoteId));

        assertEquals("Wrong id of inReplyTo note of '" + content + "': " + logMsg, noteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, draftNoteId));

        Audience audience = Audience.fromNoteId(data.getMyAccount().getOrigin(), draftNoteId);
        assertTrue("Audience of a reply to " + viewItem + "\n " + audience,
                audience.findSame(viewItem.noteViewItem.author.getActor()).isSuccess());
    }
}
