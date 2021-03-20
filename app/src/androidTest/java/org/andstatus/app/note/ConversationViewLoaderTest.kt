package org.andstatus.app.note

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import kotlin.Throws

class ConversationViewLoaderTest : ProgressPublisher {
    private var origin: Origin =  Origin.EMPTY
    private var selectedNoteId: Long = 0
    private var progressCounter: Long = 0
    @Before
    @Throws(Exception::class)
    fun setUp() {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        origin = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName).origin
        Assert.assertTrue(origin.isValid())
        selectedNoteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, DemoData.demoData.conversationEntryNoteOid)
        Assert.assertTrue("Selected note exists", selectedNoteId != 0L)
        MyLog.i(this, "setUp ended")
    }

    @Test
    fun testLoad() {
        val loader = ConversationLoaderFactory().getLoader(
                ConversationViewItem.Companion.EMPTY,  MyContextHolder.myContextHolder.getNow(), origin, selectedNoteId, false)
        progressCounter = 0
        loader.load(this)
        val list = loader.getList()
        Assert.assertTrue("List is empty", list.isNotEmpty())
        var indentFound = false
        var orderFound = false
        for (oMsg in list) {
            if (oMsg.indentLevel > 0) {
                indentFound = true
            }
            if (oMsg.mListOrder != 0) {
                orderFound = true
            }
        }
        Assert.assertTrue("Indented note found in $list", indentFound)
        Assert.assertTrue("Ordered note found in $list", orderFound)
        Assert.assertTrue(progressCounter > 0)
    }

    override fun publish(progress: String?) {
        progressCounter++
    }
}