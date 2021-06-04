package org.andstatus.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MyActionTest {

    @Test
    fun testActions() {
        assertEquals("org.andstatus.app.action.SERVICE_STATE", MyAction.SERVICE_STATE.action)
        assertEquals("org.andstatus.app.action.SYNC", MyAction.SYNC.action)
    }
}
