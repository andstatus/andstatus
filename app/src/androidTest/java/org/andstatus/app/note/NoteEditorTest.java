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
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
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
import org.andstatus.app.util.StringUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import androidx.test.espresso.action.ReplaceTextAction;
import androidx.test.espresso.action.TypeTextAction;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.andstatus.app.context.DemoData.demoData;
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
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        data = getStaticData(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                MyContextHolder.get().timelines().get(TimelineType.HOME, ma.getActor(), Origin.EMPTY).getUri());
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
                openEditor();
                editingStep1();
                break;
        }
        MyLog.v(this, "After step " + editingStep + " ended");
    }

    private void openEditor() throws InterruptedException {
        final String method = "openEditor";
        MenuItem createNoteButton = getActivity().getOptionsMenu().findItem(R.id.createNoteButton);
        assertTrue(createNoteButton != null);
        View editorView = getActivity().findViewById(R.id.note_editor);
        assertTrue(editorView != null);
        if (editorView.getVisibility() != android.view.View.VISIBLE) {
            assertTrue("Blog button is visible", createNoteButton.isVisible());
            ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
            helper.clickMenuItem(method + " opening editor", R.id.createNoteButton);
        }
        assertEquals("Editor appeared", android.view.View.VISIBLE, editorView.getVisibility());
    }

    private void editingStep1() throws InterruptedException {
        final String method = "editingStep1";
        MyLog.v(this, method + " started");

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
        helper.clickMenuItem(method + " hiding editor", R.id.saveDraftButton);
        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewInvisible(method, editorView);

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
        helper.clickMenuItem(method + " hide editor", R.id.saveDraftButton);
        ActivityTestHelper.waitViewInvisible(method + "; Editor is hidden again", editorView);
        helper.clickMenuItem(method + " clicker 5", R.id.createNoteButton);
        ActivityTestHelper.waitViewVisible(method + "; Editor appeared", editorView);
        assertTextCleared();
        helper.clickMenuItem(method + " click Discard", R.id.discardButton);
        ActivityTestHelper.waitViewInvisible(method + "; Editor hidden after discard", editorView);
        MyLog.v(this, method + " ended");
    }

    @Test
    public void testAttachImage() throws InterruptedException {
        final String method = "testAttachImage";
        MyLog.v(this, method + " started");

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
        helper.clickMenuItem(method + " hide editor", R.id.saveDraftButton);

        View editorView = getActivity().findViewById(R.id.note_editor);
        helper.clickMenuItem(method + " clicker createNoteButton", R.id.createNoteButton);
        ActivityTestHelper.waitViewVisible(method + "; Editor appeared", editorView);
        assertTextCleared();

        TestSuite.waitForIdleSync();
        final String noteName = "A note can have a title (name)";
        final String content = "Note with an attachment " + demoData.testRunUid;
        onView(withId(R.id.note_name_edit)).perform(new TypeTextAction(noteName));
        onView(withId(R.id.noteBodyEditText)).perform(new TypeTextAction(content));
        TestSuite.waitForIdleSync();

        getActivity().setSelectorActivityMock(helper);
        helper.clickMenuItem(method + " clicker attach_menu_id", R.id.attach_menu_id);
        assertNotNull(helper.waitForSelectorStart(method, ActivityRequestCode.ATTACH.id));
        getActivity().setSelectorActivityMock(null);

        Instrumentation.ActivityMonitor activityMonitor = getInstrumentation()
                .addMonitor(HelpActivity.class.getName(), null, false);

        Intent intent1 = new Intent(getActivity(), HelpActivity.class);
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().getApplicationContext().startActivity(intent1);

        Activity selectorActivity = getInstrumentation()
                .waitForMonitorWithTimeout(activityMonitor, 15000);
        assertTrue(selectorActivity != null);
        ActivityTestHelper.waitViewInvisible(method, editorView);
        DbUtils.waitMs(method, 10000);
        selectorActivity.finish();

        MyLog.i(method, "Callback from a selector");
        Intent intent2 = new Intent();
        intent2.setDataAndType(demoData.localImageTestUri2, MyContentType.IMAGE.generalMimeType);
        getActivity().runOnUiThread(() -> {
            getActivity().onActivityResult(ActivityRequestCode.ATTACH.id, Activity.RESULT_OK, intent2);
        });

        final NoteEditor editor = getActivity().getNoteEditor();
        for (int attempt=0; attempt < 4; attempt++) {
            ActivityTestHelper.waitViewVisible(method, editorView);
            // Due to a race the editor may open before this change first.
            if (demoData.localImageTestUri2.equals(editor.getData().getAttachment().getUri())) {
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        assertEquals("Image attached", demoData.localImageTestUri2, editor.getData().getAttachment().getUri());
        onView(withId(R.id.noteBodyEditText)).check(matches(withText(content + " ")));
        onView(withId(R.id.note_name_edit)).check(matches(withText(noteName)));
        helper.clickMenuItem(method + " clicker save draft", R.id.saveDraftButton);

        MyLog.v(this, method + " ended");
    }

    private void assertInitialText(final String description) throws InterruptedException {
        final NoteEditor editor = getActivity().getNoteEditor();
        TextView textView = getActivity().findViewById(R.id.noteBodyEditText);
        ActivityTestHelper.waitTextInAView(description, textView,
                MyHtml.fromContentStored(data.getContent(), TextMediaType.PLAIN));
        assertEquals(description, data.toVisibleSummary(), editor.getData().toVisibleSummary());
    }

    private void assertTextCleared() {
        final NoteEditor editor = getActivity().getNoteEditor();
        assertTrue("Editor is not null", editor != null);
        assertEquals(NoteEditorData.newEmpty(
                getActivity().getMyContext().accounts().getCurrentAccount()).toVisibleSummary(),
                editor.getData().toVisibleSummary());
    }

    @Test
    public void testContextMenuWhileEditing() throws InterruptedException {
        final String method = "testContextMenuWhileEditing";
        TestSuite.waitForListLoaded(getActivity(), 2);
        openEditor();
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

        helper.invokeContextMenuAction4ListItemId(method, listItemId, NoteContextMenuItem.COPY_AUTHOR, R.id.note_wrapper);
        String text = getClipboardText(method);
        assertThat(text, CoreMatchers.startsWith("@"));
        assertTrue(logMsg + "; Text: '" + text + "'", text.startsWith("@") && text.lastIndexOf("@") > 1);
    }

    private String getClipboardText(String methodExt) throws InterruptedException {
        final String method = "getClipboardText";
        MyLog.v(methodExt, method + " started");
        TestSuite.waitForIdleSync();
        ClipboardReader reader = new ClipboardReader();
        getInstrumentation().runOnMainSync(reader);
        MyLog.v(methodExt, method + "; clip='" + reader.clip + "'");
        if (reader.clip == null) {
            return "";
        }
        ClipData.Item item = reader.clip.getItemAt(0);
        return (StringUtils.isEmpty(item.getHtmlText()) ? item.getText() : item.getHtmlText())
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

        ActivityTestHelper<TimelineActivity> aHelper = new ActivityTestHelper<>(getActivity());
        aHelper.clickMenuItem(method + " hide editor", R.id.saveDraftButton);

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

        View editorView = getActivity().findViewById(R.id.note_editor);
        ActivityTestHelper.waitViewVisible(method + " " + logMsg, editorView);

        onView(withId(R.id.noteBodyEditText)).check(matches(withText(startsWith("@"))));

        TestSuite.waitForIdleSync();
        final String content = "Replying to you during " + demoData.testRunUid;
        EditText bodyText = editorView.findViewById(R.id.noteBodyEditText);
        // Espresso types in the centre, unfortunately, so we need to retype text
        onView(withId(R.id.noteBodyEditText)).perform(new ReplaceTextAction(bodyText.getText().toString().trim()
                + " " + content));
        TestSuite.waitForIdleSync();

        ActivityTestHelper<TimelineActivity> helper2 = new ActivityTestHelper<>(getActivity());
        helper2.clickMenuItem(method + " clicker Save draft " + logMsg, R.id.saveDraftButton);
        ActivityTestHelper.waitViewInvisible(method + " " + logMsg, editorView);

        String sql = "SELECT " + NoteTable._ID + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                + NoteTable.CONTENT + " LIKE('% " + content + "')";
        long draftNoteId = MyQuery.getLongs(sql).stream().findFirst().orElse(0L);
        assertTrue("Reply '" + content + "' was not saved: " + logMsg, draftNoteId != 0);

        assertEquals("Saved note should be in DRAFT state: " + logMsg, DownloadStatus.DRAFT,
                getDownloadStatus(draftNoteId));

        assertEquals("Wrong id of inReplyTo note of '" + content + "': " + logMsg, noteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, draftNoteId));

        Audience audience = Audience.fromNoteId(data.getMyAccount().getOrigin(), draftNoteId);
        assertTrue("Audience of a reply to " + viewItem + "\n " + audience, audience.contains(viewItem.noteViewItem.author.getActor()));
    }
}
