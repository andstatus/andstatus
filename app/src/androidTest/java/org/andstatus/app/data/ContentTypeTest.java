package org.andstatus.app.data;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.junit.Test;

import cz.msebera.android.httpclient.entity.ContentType;

import static org.junit.Assert.assertEquals;

@Travis
public class ContentTypeTest {
    @Test
    public void testApacheContentType() {
        ContentType contentType = ContentType.parse("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.create("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.parse("image/jpeg");
        assertEquals("image/jpeg", contentType.getMimeType());
    }

    @Test
    public void testMyContentType() {
        assertEquals("image/png", MyContentType.uri2MimeType(TestSuite.IMAGE1_URL, null));
        assertEquals("image/jpeg", MyContentType.filename2MimeType("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg", null));
    }
}
