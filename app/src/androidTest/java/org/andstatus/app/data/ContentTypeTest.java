package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;

import cz.msebera.android.httpclient.entity.ContentType;

@Travis
public class ContentTypeTest extends InstrumentationTestCase {
    public void testApacheContentType() {
        ContentType contentType = ContentType.parse("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.create("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.parse("image/jpeg");
        assertEquals("image/jpeg", contentType.getMimeType());
    }

    public void testMyContentType() {
        assertEquals("image/png", MyContentType.uri2MimeType(TestSuite.IMAGE1_URL, null));
        assertEquals("image/jpeg", MyContentType.filename2MimeType("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg", null));
    }
}
