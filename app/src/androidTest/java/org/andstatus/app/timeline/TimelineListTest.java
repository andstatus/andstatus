/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.context.TestSuite;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineListTest extends ActivityInstrumentationTestCase2<TimelineList> {
    public TimelineListTest() {
        super(TimelineList.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testActivityOpened() throws InterruptedException {
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        assertTrue("Timelines shown: " + getActivity().getListAdapter().getCount(),
                getActivity().getListAdapter().getCount() > 15);
    }
}
