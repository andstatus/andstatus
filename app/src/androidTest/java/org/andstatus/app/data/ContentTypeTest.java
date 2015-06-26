package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.apache.http.entity.ContentType;

public class ContentTypeTest extends InstrumentationTestCase {
    public void testApacheContentType() {
        ContentType contentType = ContentType.parse("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.create("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.parse("image/jpg");
        assertEquals("image/jpg", contentType.getMimeType());
    }

    public void testMyContentType() {
        assertEquals("image/png", MyContentType.uri2MimeType(TestSuite.LOCAL_IMAGE_TEST_URI, null));
        assertEquals("image/jpg", MyContentType.filename2MimeType("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg", null));
    }
}
