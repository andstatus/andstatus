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

package org.andstatus.app.msg;

import android.os.Bundle;
import android.test.InstrumentationTestCase;

import org.andstatus.app.IntentExtra;

/**
 * @author yvolk@yurivolkov.com
 */
public class WhichTimelinePageTest extends InstrumentationTestCase {

    public void testSaveLoad() {
        assertOne(WhichTimelinePage.SAME);
        assertOne(WhichTimelinePage.NEW);
        assertOne(WhichTimelinePage.YOUNGER);

        Bundle args = null;
        assertEquals(WhichTimelinePage.EMPTY, WhichTimelinePage.load(args));
        args = new Bundle();
        assertEquals(WhichTimelinePage.EMPTY, WhichTimelinePage.load(args));
        args.putString(IntentExtra.WHICH_PAGE.key, "234");
        assertEquals(WhichTimelinePage.EMPTY, WhichTimelinePage.load(args));
    }

    private void assertOne(WhichTimelinePage whichPage) {
        Bundle args = whichPage.save(new Bundle());
        assertEquals(whichPage, WhichTimelinePage.load(args));
    }
}
