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

import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyHtmlTest;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class NoteEditorTwitterTest extends TimelineActivityTest<ActivityViewItem> {
    private NoteEditorData data = null;
    private static final AtomicInteger editingStep = new AtomicInteger();

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);

        if (editingStep.get() != 1) {
            MyPreferences.setBeingEditedNoteId(0);
        }

        final MyAccount ma = demoData.getMyAccount(demoData.twitterTestAccountName);
        assertTrue(ma.isValid());
        assertEquals("Account should be in Twitter: " + ma, OriginType.TWITTER, ma.getOrigin().getOriginType());
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        data = getStaticData(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                MyContextHolder.get().timelines().get(TimelineType.HOME, ma.getActor(), Origin.EMPTY).getUri());
    }

    private NoteEditorData getStaticData(MyAccount ma) {
        return NoteEditorData.newEmpty(ma)
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
        if (editorView.getVisibility() != View.VISIBLE) {
            assertTrue("Blog button is visible", createNoteButton.isVisible());
            ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity());
            helper.clickMenuItem(method + " opening editor", R.id.createNoteButton);
        }
        assertEquals("Editor appeared", View.VISIBLE, editorView.getVisibility());
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
}
