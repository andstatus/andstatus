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
import org.andstatus.app.WhichPage;

/**
 * @author yvolk@yurivolkov.com
 */
public class WhichPageTest extends InstrumentationTestCase {

    public void testSaveLoad() {
        assertOne(WhichPage.SAME);
        assertOne(WhichPage.NEW);
        assertOne(WhichPage.YOUNGER);
        assertOne(WhichPage.YOUNGEST);
        assertOne(WhichPage.OLDER);
        assertOne(WhichPage.EMPTY);

        Bundle args = null;
        assertEquals(WhichPage.EMPTY, WhichPage.load(args));
        args = new Bundle();
        assertEquals(WhichPage.EMPTY, WhichPage.load(args));
        args.putString(IntentExtra.WHICH_PAGE.key, "234");
        assertEquals(WhichPage.EMPTY, WhichPage.load(args));
    }

    private void assertOne(WhichPage whichPage) {
        Bundle args = whichPage.toBundle();
        assertEquals(whichPage, WhichPage.load(args));
    }
}
