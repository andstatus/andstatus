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
package org.andstatus.app.note

import org.andstatus.app.context.TestSuite
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.DuplicationLink
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.RelativeTime
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class NoteViewItemTest {

    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testDuplicationLink() {
        val item1 = NoteViewItem(false, RelativeTime.DATETIME_MILLIS_NEVER)
        setContent(item1, HTML_BODY)
        val item2 = NoteViewItem(false, RelativeTime.DATETIME_MILLIS_NEVER)
        setContent(item2, "Some other text")
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2)
        item2.setNoteId(2)
        assertDuplicates(item1, DuplicationLink.NONE, item2)
        setContent(item2, "@<a href=\"https://bsdnode.xyz/actor/2\" class=\"h-card mention\">username</a> On duplicated posts, sent by AndStatus, please read <a href=\"https://github.com/andstatus/andstatus/issues/83\" title=\"https://github.com/andstatus/andstatus/issues/83\" class=\"attachment\" rel=\"nofollow\">https://github.com/andstatus/andstatus/issues/83</a><br /> Sorry if I misunderstood your post :-)")
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2)
        setContent(item1, "&quot;Interactions&quot; timeline in Twidere is the same or close to existing &quot;Mentions&quot; timeline in AndStatus")
        setContent(item2, "\"Interactions\" timeline in Twidere is the same or close to existing \"Mentions\" timeline in AndStatus")
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2)
        val content5 = "What is good about Android is that I can use Quitter.se via AndStatus."
        setContent(item1, content5)
        val content6 = "What is good about Android is that I can use <a href=\"https://quitter.se/\" title=\"https://quitter.se/\" class=\"attachment\" id=\"attachment-1205381\" rel=\"nofollow external\">Quitter.se</a> via AndStatus."
        setContent(item2, content6)
        assertDuplicates(item1, DuplicationLink.DUPLICATES, item2)
        assertDuplicates(item2, DuplicationLink.DUPLICATES, item1)
        val item3 = NoteViewItem(false, 1468509659000L)
        setContent(item3, content5)
        val item4 = NoteViewItem(false, 1468509658000L)
        setContent(item4, content6)
        item4.setNoteId(item2.getNoteId())
        assertDuplicates(item3, DuplicationLink.DUPLICATES, item4)
        assertDuplicates(item4, DuplicationLink.IS_DUPLICATED, item3)
        val item5 = NoteViewItem(false, item3.updatedDate)
        setContent(item5, content6)
        item5.setNoteId(item4.getNoteId())
        item5.favorited = true
        assertDuplicates(item3, DuplicationLink.DUPLICATES, item5)
        assertDuplicates(item5, DuplicationLink.IS_DUPLICATED, item3)
        item3.reblogged = true
        assertDuplicates(item3, DuplicationLink.DUPLICATES, item5)
        assertDuplicates(item5, DuplicationLink.IS_DUPLICATED, item3)
        item5.favorited = false
        assertDuplicates(item5, DuplicationLink.DUPLICATES, item3)
        assertDuplicates(item3, DuplicationLink.IS_DUPLICATED, item5)
    }

    private fun assertDuplicates(item1: NoteViewItem, duplicates: DuplicationLink, item2: NoteViewItem) {
        Assert.assertEquals("$item1 vs $item2", duplicates, item1.duplicates(Timeline.EMPTY,  Origin.EMPTY, item2))
    }

    companion object {
        private val HTML_BODY: String = """@<a href="https://bsdnode.xyz/user/2" class="h-card mention">username</a> On duplicated posts, sent by AndStatus, please read <a href="https://github.com/andstatus/andstatus/issues/83" title="https://github.com/andstatus/andstatus/issues/83" class="attachment" id="attachment-15180" rel="nofollow external">https://github.com/andstatus/andstatus/issues/83</a><br />
Sorry if I misunderstood your post :-)"""

        private fun setContent(item: NoteViewItem, content: String) {
            item.setContent(content)
            item.contentToSearch = MyHtml.getContentToSearch(content)
        }
    }
}
