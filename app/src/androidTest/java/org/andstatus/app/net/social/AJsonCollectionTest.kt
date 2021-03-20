package org.andstatus.app.net.social

import org.andstatus.app.util.RawResourceUtils
import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.*

class AJsonCollectionTest {
    @Test
    @Throws(IOException::class)
    fun testDefaultPage() {
        val c1: AJsonCollection = AJsonCollection.Companion.of(RawResourceUtils.getString(
                org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma_default))
        Assert.assertEquals(c1.toString(), "https://pleroma.site/users/AndStatus/inbox", c1.getId())
        Assert.assertEquals(c1.toString(), Optional.of("https://pleroma.site/users/AndStatus/inbox?page=true"), c1.firstPage.id)
        val c2: AJsonCollection = AJsonCollection.Companion.of(RawResourceUtils.getString(
                org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma_first))
        Assert.assertEquals(c2.toString(), "https://pleroma.site/users/AndStatus/inbox?max_id=9jPPRXLA2WW7NDFvn6&page=true", c2.getId())
        Assert.assertEquals(c2.toString(), Optional.empty<Any?>(), c2.firstPage.id)
    }
}