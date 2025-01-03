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

import android.content.Context
import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.EspressoUtils
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Test

class HelpActivityTest : ActivityTest<HelpActivity>() {
    override fun getActivityClass(): Class<HelpActivity> {
        return HelpActivity::class.java
    }

    override fun getActivityIntent(): Intent {
        TestSuite.initialize(this)
        val intent = Intent()
        intent.putExtra(HelpActivity.Companion.EXTRA_IS_FIRST_ACTIVITY, true)
        intent.putExtra(HelpActivity.Companion.EXTRA_HELP_PAGE_INDEX, HelpActivity.Companion.PAGE_CHANGELOG)
        return intent
    }

    @After
    fun tearDown() {
        closeAllActivities(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun test() {
        EspressoUtils.waitForIdleSync()
        val mFlipper = mActivityRule.activity.findViewById<ViewPager?>(R.id.help_flipper)
        Assert.assertNotNull(mFlipper)
        DbUtils.waitMs("test", 200)
        Assert.assertEquals(
            "At Changelog page",
            HelpActivity.Companion.PAGE_CHANGELOG.toLong(),
            mFlipper.currentItem.toLong()
        )
        Espresso.onView(ViewMatchers.withId(R.id.button_help_learn_more)).perform(ViewActions.click())
        DbUtils.waitMs("test", 200)
        Assert.assertEquals(
            "At User Guide",
            HelpActivity.Companion.PAGE_USER_GUIDE.toLong(),
            mFlipper.currentItem.toLong()
        )
        Espresso.onView(ViewMatchers.withId(R.id.button_help_learn_more)).perform(ViewActions.click())
        Assert.assertEquals("At Logo page", HelpActivity.Companion.PAGE_LOGO.toLong(), mFlipper.currentItem.toLong())
        Espresso.onView(ViewMatchers.withId(R.id.splash_application_version)).check(
            ViewAssertions.matches(
                ViewMatchers.withText(
                    CoreMatchers.containsString(myContextHolder.executionMode.code)
                )
            )
        )
        DbUtils.waitMs("test", 500)
        val helper = ActivityTestHelper(
            mActivityRule.activity,
            MySettingsActivity::class.java
        )
        //       openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());
        Espresso.onView(ViewMatchers.withId(R.id.preferences_menu_id)).perform(ViewActions.click())
        val nextActivity = helper.waitForNextActivity("Clicking on Settings menu item", 10000)
        DbUtils.waitMs("test", 500)
        nextActivity.finish()
    }

    companion object {

        /**
         * Based on http://stackoverflow.com/questions/14001963/finish-all-activities-at-a-time
         */
        fun closeAllActivities(context: Context) {
            context.startActivity(
                MyAction.CLOSE_ALL_ACTIVITIES.newIntent()
                    .setClass(context, FirstActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
