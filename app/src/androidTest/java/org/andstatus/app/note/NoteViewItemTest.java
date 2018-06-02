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

package org.andstatus.app.note;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.util.MyHtml;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author yvolk@yurivolkov.com
 */
public class NoteViewItemTest {

    private static final String HTML_BODY =
            "@<a href=\"https://bsdnode.xyz/user/2\" class=\"h-card mention\">username</a> " +
            "On duplicated posts, sent by AndStatus, please read <a href=\"https://github.com/andstatus/andstatus/issues/83\" " +
            "title=\"https://github.com/andstatus/andstatus/issues/83\" class=\"attachment\" id=\"attachment-15180\" " +
            "rel=\"nofollow external\">https://github.com/andstatus/andstatus/issues/83</a><br />\n" +
            "Sorry if I misunderstood your post :-)";

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void testDuplicationLink() {
        NoteViewItem item1 = new NoteViewItem(false);
        setContent(item1, HTML_BODY);
        NoteViewItem item2 = new NoteViewItem(false);
        setContent(item2, "Some other text");
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);

        item2.setNoteId(2);
        assertDuplicates(item1, DuplicationLink.NONE, item2);

        setContent(item2, "@<a href=\"https://bsdnode.xyz/actor/2\" class=\"h-card mention\">username</a> On duplicated posts, sent by AndStatus, please read <a href=\"https://github.com/andstatus/andstatus/issues/83\" title=\"https://github.com/andstatus/andstatus/issues/83\" class=\"attachment\" rel=\"nofollow\">https://github.com/andstatus/andstatus/issues/83</a><br /> Sorry if I misunderstood your post :-)");
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);

        setContent(item1, "&quot;Interactions&quot; timeline in Twidere is the same or close to existing &quot;Mentions&quot; timeline in AndStatus");
        setContent(item2, "\"Interactions\" timeline in Twidere is the same or close to existing \"Mentions\" timeline in AndStatus");
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);

        setContent(item1, "What is good about Android is that I can use Quitter.se via AndStatus.");
        setContent(item2, "What is good about Android is that I can use <a href=\"https://quitter.se/\" title=\"https://quitter.se/\" class=\"attachment\" id=\"attachment-1205381\" rel=\"nofollow external\">Quitter.se</a> via AndStatus.");
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);

        item1.updatedDate = 1468509659000L;
        item2.updatedDate = 1468509658000L;
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);
        assertDuplicates(item2, DuplicationLink.IS_DUPLICATED, item1);
        item2.updatedDate = item1.updatedDate;

        item2.favorited = true;
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);
        assertDuplicates(item2, DuplicationLink.IS_DUPLICATED, item1);

        item1.reblogged = true;
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2);
        assertDuplicates(item2, DuplicationLink.IS_DUPLICATED, item1);

        item2.favorited = false;
        assertDuplicates(item2, DuplicationLink.DUPLICATES, item1);
        assertDuplicates(item1, DuplicationLink.IS_DUPLICATED, item2);
    }

    private static void setContent(NoteViewItem item, String content) {
        item.setContent(content);
        item.contentToSearch = MyHtml.getContentToSearch(content);
    }

    private void assertDuplicates(NoteViewItem item1, DuplicationLink duplicates, NoteViewItem item2) {
        assertEquals(item1.toString() + " vs " + item2, duplicates, item1.duplicates(item2));
    }
}
