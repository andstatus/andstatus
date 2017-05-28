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
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.view.View;
import android.widget.ViewFlipper;

import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.junit.After;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HelpActivityTest extends ActivityTest<HelpActivity> {

    @Override
    protected Class<HelpActivity> getActivityClass() {
        return HelpActivity.class;
    }

    @Override
    protected Intent getActivityIntent() {
        TestSuite.initializeWithData(this);
        Intent intent = new Intent();
        intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, HelpActivity.PAGE_INDEX_CHANGELOG);
        return intent;
    }

    @After
    public void tearDown() throws Exception {
        MySettingsActivity.closeAllActivities(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @Test
    public void test() throws Throwable {
        ViewFlipper mFlipper = ((ViewFlipper) mActivityRule.getActivity().findViewById(R.id.help_flipper));
        assertTrue(mFlipper != null);
        assertEquals("At Changelog page", HelpActivity.PAGE_INDEX_CHANGELOG, mFlipper.getDisplayedChild());
        View changeLogView = mActivityRule.getActivity().findViewById(R.id.changelog);
        assertTrue(changeLogView != null);
        DbUtils.waitMs("test", 500);

        onView(withId(R.id.button_help_learn_more)).perform(click());
        assertEquals("At Logo page", HelpActivity.PAGE_INDEX_LOGO, mFlipper.getDisplayedChild());
        onView(withId(R.id.splash_application_version)).check(matches(withText(containsString(MyContextHolder
                .getExecutionMode().code))));

        DbUtils.waitMs("test", 500);

        ActivityTestHelper<HelpActivity> helper = new ActivityTestHelper<>(mActivityRule.getActivity(),
                MySettingsActivity.class);
        //       openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());
        onView(withId(R.id.preferences_menu_id)).perform(click());
        Activity nextActivity = helper.waitForNextActivity("Clicking on Settings menu item", 10000);
        DbUtils.waitMs("test", 500);
        nextActivity.finish();
    }
}
