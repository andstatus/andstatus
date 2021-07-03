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
package org.andstatus.app

import android.app.Activity
import android.app.Instrumentation.ActivityMonitor
import android.content.Intent
import android.provider.BaseColumns
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.test.SelectorActivityStub
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.util.MyLog
import org.junit.Assert
import java.util.concurrent.atomic.AtomicBoolean

class ActivityTestHelper<T : MyActivity> : SelectorActivityStub {
    private var mActivity: T
    private var activityMonitor: ActivityMonitor? = null

    @Volatile
    private var selectorIntent: Intent? = null

    @Volatile
    private var selectorRequestCode = 0

    constructor(activity: T) : super() {
        mActivity = activity
    }

    constructor(activity: T, classOfActivityToMonitor: Class<out Activity>) : super() {
        addMonitor(classOfActivityToMonitor)
        mActivity = activity
    }

    fun addMonitor(classOfActivity: Class<out Activity>): ActivityMonitor {
        return InstrumentationRegistry.getInstrumentation().addMonitor(classOfActivity.getName(), null, false).also {
            activityMonitor = it
        }
    }

    fun waitForNextActivity(method: String, timeOut: Long): Activity {
        val nextActivity = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, timeOut)
        MyLog.v(this, "$method-Log after waitForMonitor: $nextActivity")
        Assert.assertNotNull("Next activity is opened and captured", nextActivity)
        activityMonitor = null
        return nextActivity
    }

    fun clickMenuItem(method: String, menuItemResourceId: Int): Boolean {
        Assert.assertTrue(menuItemResourceId != 0)
        TestSuite.waitForIdleSync()
        MyLog.v(this, "$method-Log before run clickers")
        val clicked = AtomicBoolean(false)
        clicked.set(InstrumentationRegistry.getInstrumentation().invokeMenuActionSync(mActivity, menuItemResourceId, 0))
        if (clicked.get()) {
            MyLog.i(this, "$method-Log instrumentation clicked")
        } else {
            MyLog.i(this, "$method-Log instrumentation couldn't click")
        }
        if (!clicked.get()) {
            val menu = mActivity.getOptionsMenu()
            if (menu != null) {
                val clicker = MenuItemClicker(method, menu, menuItemResourceId)
                InstrumentationRegistry.getInstrumentation().runOnMainSync(clicker)
                clicked.set(clicker.clicked)
                if (clicked.get()) {
                    MyLog.i(this, "$method-Log performIdentifierAction clicked")
                } else {
                    MyLog.i(this, "$method-Log performIdentifierAction couldn't click")
                }
            }
        }
        if (!clicked.get()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(object : Runnable {
                override fun run() {
                    val msg = "$method-Log onOptionsItemSelected"
                    MyLog.v(method, msg)
                    try {
                        val menuItem = MenuItemStub(menuItemResourceId)
                        mActivity.onOptionsItemSelected(menuItem)
                        clicked.set(menuItem.called())
                        if (clicked.get()) {
                            MyLog.i(this, "$msg clicked")
                        } else {
                            MyLog.i(this, "$msg couldn't click")
                        }
                    } catch (e: Exception) {
                        MyLog.e(msg, e)
                    }
                }
            })
        }
        TestSuite.waitForIdleSync()
        return clicked.get()
    }

    fun waitForSelectorStart(method: String?, requestCode: Int): Intent? {
        var ok = false
        for (i in 0..19) {
            if (selectorRequestCode == requestCode) {
                ok = true
                break
            }
            if (DbUtils.waitMs(method, 2000)) {
                break
            }
        }
        MyLog.v(method, if (ok) "Request received: " + selectorIntent.toString() else "Request wasn't received")
        selectorRequestCode = 0
        return if (ok) selectorIntent else null
    }

    override fun startActivityForResult(intent: Intent?, requestCode: Int) {
        selectorIntent = intent
        selectorRequestCode = requestCode
    }

    private class MenuItemClicker(private val method: String, private val menu: Menu, private val menuItemResourceId: Int) : Runnable {
        @Volatile
        var clicked = false
        override fun run() {
            MyLog.v(this, "$method-Log before click")
            clicked = menu.performIdentifierAction(menuItemResourceId, 0)
        }
    }

    companion object {
        fun waitViewVisible(method: String, view: View?): Boolean {
            if (view == null) throw IllegalStateException("View is null")
            var ok = false
            for (i in 0..19) {
                if (view.visibility == View.VISIBLE) {
                    ok = true
                    break
                }
                if (DbUtils.waitMs(method, 2000)) {
                    break
                }
            }
            MyLog.v(method, if (ok) "Visible" else "Invisible")
            Assert.assertTrue("$method; View is visible", ok)
            TestSuite.waitForIdleSync()
            return ok
        }

        fun waitViewInvisible(method: String, view: View?): Boolean {
            if (view == null) throw IllegalStateException("View is null")
            var ok = false
            for (i in 0..19) {
                if (view.visibility != View.VISIBLE) {
                    ok = true
                    break
                }
                if (DbUtils.waitMs(method, 2000)) {
                    break
                }
            }
            MyLog.v(method, if (ok) "Invisible" else "Visible")
            Assert.assertTrue("$method; View is invisible", ok)
            TestSuite.waitForIdleSync()
            return ok
        }

        fun waitTextInAView(method: String, view: TextView, textToFind: String): Boolean {
            var ok = false
            var textFound = ""
            for (i in 0..19) {
                textFound = view.getText().toString()
                if (textFound.contains(textToFind)) {
                    ok = true
                    break
                }
                if (DbUtils.waitMs(method, 2000)) {
                    break
                }
            }
            MyLog.v(method, (if (ok) "Found" else "Not found") + " text '" + textToFind + "' in '" + textFound + "'")
            Assert.assertTrue("$method; Not found text '$textToFind' in '$textFound'", ok)
            return ok
        }

        fun closeContextMenu(activity: Activity) {
            val runnable = Runnable { activity.closeContextMenu() }
            activity.runOnUiThread(runnable)
            TestSuite.waitForIdleSync()
        }

        fun waitAndGetIdOfStoredNote(method: String?, content: String?): Long {
            val sql = ("SELECT " + BaseColumns._ID + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                    + NoteTable.CONTENT + " LIKE('%" + content + "%')")
            var noteId: Long = 0
            for (attempt in 0..9) {
                noteId = MyQuery.getLongs(sql).stream().findFirst().orElse(0L)
                if (noteId != 0L) break
                if (DbUtils.waitMs(method, 2000)) break
            }
            Assert.assertTrue("$method: Note '$content' was not saved", noteId != 0L)
            return noteId
        }

        fun <T : ViewItem<T>> hideEditorAndSaveDraft(method: String, activity: TimelineActivity<T>): View {
            return try {
                val editorView = activity.findViewById<View?>(R.id.note_editor)
                if (editorView.visibility != View.VISIBLE) return editorView
                val helper = ActivityTestHelper<TimelineActivity<*>>(activity)
                helper.clickMenuItem("$method hiding editor", R.id.saveDraftButton)
                waitViewInvisible(method, editorView)
                editorView
            } catch (e: Exception) {
                Assert.fail("$method failed to hide editor. $e")
                throw e
            }
        }

        fun <T : ViewItem<T>> clickSendButton(logMsg: String, activity: TimelineActivity<T>): View {
            val method = "Click Send button; $logMsg"
            return try {
                val editorView = activity.findViewById<View?>(R.id.note_editor)
                if (editorView.visibility != View.VISIBLE) Assert.fail("EditorView is invisible $method")
                val helper = ActivityTestHelper<TimelineActivity<*>>(activity)
                helper.clickMenuItem(method, R.id.noteSendButton)
                waitViewInvisible(method, editorView)
                editorView
            } catch (e: Exception) {
                Assert.fail("$method failed to Click Send button. $e")
                throw e
            }
        }

        fun <T : ViewItem<T>> openEditor(logMsg: String?, activity: TimelineActivity<T>): View {
            val method = "openEditor $logMsg"
            return try {
                val createNoteButton = activity.getOptionsMenu()?.findItem(R.id.createNoteButton)
                        ?: throw IllegalStateException("createNoteButton not found")
                val editorView = activity.findViewById<View?>(R.id.note_editor)
                Assert.assertTrue(editorView != null)
                if (editorView.visibility != View.VISIBLE) {
                    Assert.assertTrue("Blog button is visible", createNoteButton.isVisible)
                    val helper = ActivityTestHelper<TimelineActivity<*>>(activity)
                    helper.clickMenuItem("$method opening editor", R.id.createNoteButton)
                }
                Assert.assertEquals("Editor appeared", View.VISIBLE.toLong(), editorView.visibility.toLong())
                editorView
            } catch (e: Exception) {
                Assert.fail("$method failed to open editor. $e")
                throw e
            }
        }
    }
}
