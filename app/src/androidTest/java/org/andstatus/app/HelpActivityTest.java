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
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.ViewFlipper;

import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;

public class HelpActivityTest extends ActivityInstrumentationTestCase2<HelpActivity> {

    public HelpActivityTest() {
        super(HelpActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        
        Intent intent = new Intent();
        intent.putExtra(HelpActivity.EXTRA_IS_FIRST_ACTIVITY, true);
        intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, HelpActivity.PAGE_INDEX_CHANGELOG);
        setActivityIntent(intent);
    }

    @Override
    protected void tearDown() throws Exception {
        MySettingsActivity.closeAllActivities(getInstrumentation().getTargetContext());
        super.tearDown();
    }

    public void test() throws Throwable {
        ViewFlipper mFlipper = ((ViewFlipper) getActivity().findViewById(R.id.help_flipper));
        assertTrue(mFlipper != null);
        assertEquals("At Changelog page", HelpActivity.PAGE_INDEX_CHANGELOG, mFlipper.getDisplayedChild());
        View changeLogView = getActivity().findViewById(R.id.changelog);
        assertTrue(changeLogView != null);
        DbUtils.waitMs("test", 500);

        ActivityTestHelper<HelpActivity> helper = new ActivityTestHelper<HelpActivity>(this, MySettingsActivity.class);
        assertTrue("Click on ActionBar item", helper.clickMenuItem("Clicking on Settings menu item", R.id.preferences_menu_id));
        Activity nextActivity = helper.waitForNextActivity("Clicking on Settings menu item", 10000);
        DbUtils.waitMs("test", 500);
        nextActivity.finish();
    }

}
