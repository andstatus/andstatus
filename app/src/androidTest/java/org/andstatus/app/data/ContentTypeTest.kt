package org.andstatus.app.data

import android.content.ContentResolver
import android.net.Uri
import cz.msebera.android.httpclient.entity.ContentType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.UriUtils
import org.junit.Assert
import org.junit.Test

class ContentTypeTest {
    @Test
    fun testApacheContentType() {
        var contentType = ContentType.parse("image/png")
        Assert.assertEquals("image/png", contentType.mimeType)
        contentType = ContentType.create("video/mp4")
        Assert.assertEquals("video/mp4", contentType.mimeType)
        contentType = ContentType.parse("image/jpeg")
        Assert.assertEquals("image/jpeg", contentType.mimeType)
    }

    @Test
    fun testMyContentType() {
        val contentResolver: ContentResolver = MyContextHolder.Companion.myContextHolder.getNow().context().getContentResolver()
        Assert.assertEquals("image/png", uri2MimeType(contentResolver, DemoData.Companion.demoData.image1Url))
        Assert.assertEquals("image/jpeg", uri2MimeType(null,
                UriUtils.fromString("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg")))
        Assert.assertEquals("image/gif", uri2MimeType(contentResolver, DemoData.Companion.demoData.localGifTestUri))
        Assert.assertEquals("image/gif", MyContentType.Companion.uri2MimeType(contentResolver, DemoData.Companion.demoData.localGifTestUri,
                MyContentType.UNKNOWN.generalMimeType))
        Assert.assertEquals("image/gif", MyContentType.Companion.uri2MimeType(contentResolver, DemoData.Companion.demoData.localGifTestUri,
                MyContentType.IMAGE.generalMimeType))
        val uriTxt = Uri.parse("http://example.com/something_txt")
        Assert.assertEquals("image/*", MyContentType.Companion.uri2MimeType(contentResolver, uriTxt, MyContentType.IMAGE.generalMimeType))
        Assert.assertEquals("text/plain", MyContentType.Companion.uri2MimeType(contentResolver, uriTxt, MyContentType.TEXT.generalMimeType))
    }
}