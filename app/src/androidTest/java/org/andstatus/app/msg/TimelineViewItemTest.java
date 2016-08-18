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

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.widget.DuplicationLink;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineViewItemTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testDuplicationLink() {
        TimelineViewItem item1 = new TimelineViewItem();
        item1.body = "@<a href=\"https://bsdnode.xyz/user/2\" class=\"h-card mention\">username</a> On duplicated posts, sent by AndStatus, please read <a href=\"https://github.com/andstatus/andstatus/issues/83\" title=\"https://github.com/andstatus/andstatus/issues/83\" class=\"attachment\" id=\"attachment-15180\" rel=\"nofollow external\">https://github.com/andstatus/andstatus/issues/83</a><br />\n" +
                "Sorry if I misunderstood your post :-)";
        TimelineViewItem item2 = new TimelineViewItem();
        item2.body = "Some other text";
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);

        item2.setMsgId(2);
        assertDuplicates(item1, item2, DuplicationLink.NONE);

        item2.body = "thisUser favorited something by thatUser: " + item1.body;
        assertDuplicates(item1, item2, DuplicationLink.IS_DUPLICATED);
        assertDuplicates(item2, item1, DuplicationLink.DUPLICATES);

        item2.body = "@<a href=\"https://bsdnode.xyz/user/2\" class=\"h-card mention\">username</a> On duplicated posts, sent by AndStatus, please read <a href=\"https://github.com/andstatus/andstatus/issues/83\" title=\"https://github.com/andstatus/andstatus/issues/83\" class=\"attachment\" rel=\"nofollow\">https://github.com/andstatus/andstatus/issues/83</a><br /> Sorry if I misunderstood your post :-)";
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);

        item1.body = "&quot;Interactions&quot; timeline in Twidere is the same or close to existing &quot;Mentions&quot; timeline in AndStatus";
        item2.body = "\"Interactions\" timeline in Twidere is the same or close to existing \"Mentions\" timeline in AndStatus";
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);

        item1.body = "What is good about Android is that I can use Quitter.se via AndStatus.";
        item2.body = "What is good about Android is that I can use <a href=\"https://quitter.se/\" title=\"https://quitter.se/\" class=\"attachment\" id=\"attachment-1205381\" rel=\"nofollow external\">Quitter.se</a> via AndStatus.";
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);

        item1.createdDate = 1468509659000L;
        item2.createdDate = 1468509658000L;
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);
        assertDuplicates(item2, item1, DuplicationLink.IS_DUPLICATED);
        item2.createdDate = item1.createdDate;

        item2.favorited = true;
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);
        assertDuplicates(item2, item1, DuplicationLink.IS_DUPLICATED);

        item1.reblogged = true;
        assertDuplicates(item1, item2, DuplicationLink.DUPLICATES);
        assertDuplicates(item2, item1, DuplicationLink.IS_DUPLICATED);

        item2.favorited = false;
        assertDuplicates(item2, item1, DuplicationLink.DUPLICATES);
        assertDuplicates(item1, item2, DuplicationLink.IS_DUPLICATED);
    }

    protected void assertDuplicates(TimelineViewItem item1, TimelineViewItem item2, DuplicationLink duplicates) {
        assertEquals(item1.toString() + " vs " + item2, duplicates, item1.duplicates(item2));
    }
}
