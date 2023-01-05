/*
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
package org.andstatus.app.timeline

import android.content.Intent
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ReplaceTextAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import org.andstatus.app.ActivityTestHelper
import org.andstatus.app.R
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class PublicTimelineActivityTest : TimelineActivityTest<ActivityViewItem>() {

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        val myContext = myContextHolder.getBlocking()
        val origin: Origin = myContext.origins.fromName(DemoData.demoData.gnusocialTestOriginName)
        Assert.assertTrue(origin.toString(), origin.isValid)
        MyLog.i(this, "setUp ended")
        val timeline = myContext.timelines.get(TimelineType.PUBLIC, Actor.EMPTY, origin)
        timeline.save(myContext)
        return Intent(Intent.ACTION_VIEW, timeline.getUri())
    }

    @Test
    fun testGlobalSearchInOptionsMenu() {
        oneSearchTest("testGlobalSearchInOptionsMenu", DemoData.demoData.globalPublicNoteText, true)
    }

    @Test
    fun testSearch() {
        oneSearchTest("testSearch", DemoData.demoData.publicNoteText, false)
    }

    private fun oneSearchTest(method: String, noteText: String, internetSearch: Boolean) {
        val menu_id: Int = R.id.search_menu_id
        Assert.assertTrue("MyService is available", MyServiceManager.Companion.isServiceAvailable())
        TestSuite.waitForListLoaded(activity, 2)
        Assert.assertEquals(TimelineType.PUBLIC, activity.getTimeline().timelineType)
        Assert.assertFalse("Screen is locked", TestSuite.isScreenLocked(activity))
        val helper = ActivityTestHelper<TimelineActivity<*>>(
            activity,
            TimelineActivity::class.java
        )
        helper.clickMenuItem(method, menu_id)
        Espresso.onView(ViewMatchers.withId(R.id.internet_search)).perform(EspressoUtils.setChecked(internetSearch))
        Espresso.onView(ViewMatchers.withResourceName("search_text"))
            .perform(ReplaceTextAction(noteText), ViewActions.pressImeActionButton())
        val nextActivity = helper.waitForNextActivity(method, 40000) as TimelineActivity<*>
        waitForButtonClickedEvidence(nextActivity, method, noteText)
        assertNotesArePublic(nextActivity, noteText)
        nextActivity.finish()
    }

    @Volatile
    private var stringFound: String = ""
    private fun waitForButtonClickedEvidence(
        timelineActivity: TimelineActivity<*>, caption: String?,
        queryString: String
    ) {
        val method = "waitForButtonClickedEvidence"
        var found = false
        Assert.assertNotNull("Timeline activity is null", timelineActivity)
        for (attempt in 0..5) {
            EspressoUtils.waitForIdleSync()
            val probe = Runnable {
                val item = timelineActivity.findViewById<TextView?>(R.id.timelineTypeButton)
                if (item != null) {
                    stringFound = item.text.toString()
                }
            }
            timelineActivity.runOnUiThread(probe)
            if (stringFound.contains(queryString)) {
                found = true
                break
            }
            DbUtils.waitMs(method, 1000 * (attempt + 1))
        }
        Assert.assertTrue(
            caption + ", query:'" + queryString
                + "', found:'" + stringFound + "'", found
        )
    }

    private fun assertNotesArePublic(timelineActivity: TimelineActivity<*>, publicNoteText: String) {
        val method = "assertNotesArePublic"
        var msgCount = 0
        for (attempt in 0..2) {
            EspressoUtils.waitForIdleSync()
            msgCount = oneAttempt(timelineActivity, publicNoteText)
            if (msgCount > 0 || DbUtils.waitMs(method, 2000 * (attempt + 1))) {
                break
            }
        }
        Assert.assertTrue("Notes should be found", msgCount > 0)
    }

    private fun oneAttempt(timelineActivity: TimelineActivity<*>, publicNoteText: String): Int {
        val list = timelineActivity.findViewById<ViewGroup?>(android.R.id.list)
        var msgCount = 0
        for (index in 0 until list.childCount) {
            val noteView = list.getChildAt(index)
            val bodyView = noteView.findViewById<TextView?>(R.id.note_body)
            val viewItem: BaseNoteViewItem<*> = ListActivityTestHelper.toBaseNoteViewItem(
                timelineActivity.getListAdapter().getItem(noteView)
            )
            if (bodyView != null) {
                Assert.assertTrue(
                    """Note #${viewItem.getId()} '${viewItem.getContent()}' contains '$publicNoteText'
$viewItem""",
                    viewItem.getContent().toString().contains(publicNoteText)
                )
                Assert.assertNotEquals(
                    """Note #${viewItem.getId()} '${viewItem.getContent()}' is private
$viewItem""", Visibility.PRIVATE,
                    Visibility.Companion.fromNoteId(viewItem.getId())
                )
                msgCount++
            }
        }
        MyLog.v(this, "Public notes with '$publicNoteText' found: $msgCount")
        return msgCount
    }
}
