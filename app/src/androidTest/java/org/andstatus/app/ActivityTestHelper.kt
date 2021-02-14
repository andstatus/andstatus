/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.test.SelectorActivityMock;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ActivityTestHelper<T extends MyActivity> implements SelectorActivityMock {
    private T mActivity;
    private ActivityMonitor activityMonitor = null;

    private volatile Intent selectorIntent = null;
    private volatile int selectorRequestCode = 0;

    public ActivityTestHelper(T activity) {
        super();
        mActivity = activity;
    }

    public ActivityTestHelper(T activity, Class<? extends Activity> classOfActivityToMonitor) {
        super();
        addMonitor(classOfActivityToMonitor);
        mActivity = activity;
    }

    public static boolean waitViewVisible(String method, View view) throws InterruptedException {
        assertTrue(view != null);
        boolean ok = false;
        for (int i = 0; i < 20; i++) {
            if (view.getVisibility() == View.VISIBLE) {
                ok = true;
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        MyLog.v(method, ok ? "Visible" : "Invisible");
        assertTrue(method + "; View is visible", ok);
        TestSuite.waitForIdleSync();
        return ok;
    }

    public static boolean waitViewInvisible(String method, View view) throws InterruptedException {
        assertTrue(view != null);
        boolean ok = false;
        for (int i = 0; i < 20; i++) {
            if (view.getVisibility() != View.VISIBLE) {
                ok = true;
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        MyLog.v(method, (ok ? "Invisible" : "Visible"));
        assertTrue(method + "; View is invisible", ok);
        TestSuite.waitForIdleSync();
        return ok;
    }

    public static boolean waitTextInAView(String method, TextView view, String textToFind) throws InterruptedException {
        boolean ok = false;
        String textFound = "";
        for (int i = 0; i < 20; i++) {
            textFound = view.getText().toString();
            if (textFound.contains(textToFind)) {
                ok = true;
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        MyLog.v(method, (ok ? "Found" : "Not found") + " text '" + textToFind + "' in '" + textFound + "'");
        assertTrue(method + "; Not found text '" + textToFind + "' in '" + textFound + "'", ok);
        return ok;
    }

    public static void closeContextMenu(final Activity activity) throws InterruptedException {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                activity.closeContextMenu();
            }
        };
        activity.runOnUiThread(runnable);
        TestSuite.waitForIdleSync();
    }

    public static long waitAndGetIdOfStoredNote(String method, String content) {
        String sql = "SELECT " + NoteTable._ID + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                + NoteTable.CONTENT + " LIKE('%" + content + "%')";
        long noteId = 0;
        for (int attempt=0; attempt < 10; attempt++) {
            noteId = MyQuery.getLongs(sql).stream().findFirst().orElse(0L);
            if (noteId != 0) break;
            if (DbUtils.waitMs(method, 2000)) break;
        }
        assertTrue(method + ": Note '" + content + "' was not saved", noteId != 0);
        return noteId;
    }

    public static <T extends ViewItem<T>> View hideEditorAndSaveDraft(String method, TimelineActivity<T> activity) {
        try {
            View editorView = activity.findViewById(R.id.note_editor);
            if (editorView.getVisibility() != View.VISIBLE) return editorView;

            ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(activity);
            helper.clickMenuItem(method + " hiding editor", R.id.saveDraftButton);
            waitViewInvisible(method, editorView);
            return editorView;
        } catch (Exception e) {
            fail(method + " failed to hide editor. " + e);
            return null;
        }
    }

    public static <T extends ViewItem<T>> View clickSendButton(String logMsg, TimelineActivity<T> activity) {
        String method = "Click Send button; " + logMsg;
        try {
            View editorView = activity.findViewById(R.id.note_editor);
            if (editorView.getVisibility() != View.VISIBLE) fail("EditorView is invisible " + method);

            ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(activity);
            helper.clickMenuItem(method, R.id.noteSendButton);
            waitViewInvisible(method, editorView);
            return editorView;
        } catch (Exception e) {
            fail(method + " failed to Click Send button. " + e);
            return null;
        }
    }

    public static <T extends ViewItem<T>> View openEditor(String logMsg, TimelineActivity<T> activity) {
        final String method = "openEditor " + logMsg;
        try {
            MenuItem createNoteButton = activity.getOptionsMenu().findItem(R.id.createNoteButton);
            assertTrue(createNoteButton != null);
            View editorView = activity.findViewById(R.id.note_editor);
            assertTrue(editorView != null);
            if (editorView.getVisibility() != View.VISIBLE) {
                assertTrue("Blog button is visible", createNoteButton.isVisible());
                ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(activity);
                helper.clickMenuItem(method + " opening editor", R.id.createNoteButton);
            }
            assertEquals("Editor appeared", View.VISIBLE, editorView.getVisibility());
            return editorView;
        } catch (Exception e) {
            fail(method + " failed to open editor. " + e);
            return null;
        }
    }

    public ActivityMonitor addMonitor(Class<? extends Activity> classOfActivity) {
        activityMonitor = InstrumentationRegistry.getInstrumentation().addMonitor(classOfActivity.getName(), null, false);
        return activityMonitor;
    }
    
    public Activity waitForNextActivity(String method, long timeOut) throws InterruptedException {
        Activity nextActivity = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, timeOut);
        MyLog.v(this, method + "-Log after waitForMonitor: " + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        activityMonitor = null;
        return nextActivity;
    }

    public boolean clickMenuItem(final String method, final int menuItemResourceId) throws InterruptedException {
        assertTrue(menuItemResourceId != 0);
        TestSuite.waitForIdleSync();
        MyLog.v(this, method + "-Log before run clickers");

        final AtomicBoolean clicked = new AtomicBoolean(false);
        clicked.set(InstrumentationRegistry.getInstrumentation().invokeMenuActionSync(mActivity, menuItemResourceId, 0));
        if (clicked.get()) {
            MyLog.i(this, method + "-Log instrumentation clicked");
        } else {
            MyLog.i(this, method + "-Log instrumentation couldn't click");
        }

        if (!clicked.get()) {
            Menu menu = mActivity.getOptionsMenu();
            if (menu != null) {
                MenuItemClicker clicker = new MenuItemClicker(method, menu, menuItemResourceId);
                InstrumentationRegistry.getInstrumentation().runOnMainSync(clicker);
                clicked.set(clicker.clicked);
                if (clicked.get()) {
                    MyLog.i(this, method + "-Log performIdentifierAction clicked");
                } else {
                    MyLog.i(this, method + "-Log performIdentifierAction couldn't click");
                }
            }
        }

        if (!clicked.get()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    final String msg = method + "-Log onOptionsItemSelected";
                    MyLog.v(method, msg);
                    try {
                        MenuItemMock menuItem = new MenuItemMock(menuItemResourceId);
                        mActivity.onOptionsItemSelected(menuItem);
                        clicked.set(menuItem.called());
                        if (clicked.get()) {
                            MyLog.i(this, msg + " clicked");
                        } else {
                            MyLog.i(this, msg + " couldn't click");
                        }
                    } catch (Exception e) {
                        MyLog.e(msg, e);
                    }
                }
            });
        }
        TestSuite.waitForIdleSync();
        return clicked.get();
    }

    public Intent waitForSelectorStart(String method, int requestCode) throws InterruptedException {
        boolean ok = false;
        for (int i = 0; i < 20; i++) {
            if (selectorRequestCode == requestCode) {
                ok = true;
                break;
            }
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
        }
        MyLog.v(method, (ok ? "Request received: " + selectorIntent.toString() : "Request wasn't received"));
        selectorRequestCode = 0;
        return ok ? selectorIntent : null;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        selectorIntent = intent;
        selectorRequestCode = requestCode;
    }

    private static class MenuItemClicker implements Runnable {
        private String method;
        private Menu menu;
        private int menuItemResourceId;

        volatile boolean clicked = false;

        public MenuItemClicker(String method, Menu menu, int menuItemResourceId) {
            this.method = method;
            this.menu = menu;
            this.menuItemResourceId = menuItemResourceId;
        }

        @Override
        public void run() {
            MyLog.v(this, method + "-Log before click");
            clicked = menu.performIdentifierAction(menuItemResourceId, 0);
        }
    }
}
