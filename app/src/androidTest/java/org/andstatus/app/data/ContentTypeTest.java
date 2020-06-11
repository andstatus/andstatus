package org.andstatus.app.data;

import android.content.ContentResolver;
import android.net.Uri;

import org.andstatus.app.util.UriUtils;
import org.junit.Test;

import cz.msebera.android.httpclient.entity.ContentType;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.data.MyContentType.IMAGE;
import static org.andstatus.app.data.MyContentType.TEXT;
import static org.junit.Assert.assertEquals;

public class ContentTypeTest {
    @Test
    public void testApacheContentType() {
        ContentType contentType = ContentType.parse("image/png");
        assertEquals("image/png", contentType.getMimeType());
        contentType = ContentType.create("video/mp4");
        assertEquals("video/mp4", contentType.getMimeType());
        contentType = ContentType.parse("image/jpeg");
        assertEquals("image/jpeg", contentType.getMimeType());
    }

    @Test
    public void testMyContentType() {
        final ContentResolver contentResolver = myContextHolder.getNow().context().getContentResolver();
        assertEquals("image/png", MyContentType.uri2MimeType(contentResolver, demoData.image1Url));
        assertEquals("image/jpeg", MyContentType.uri2MimeType(null,
                UriUtils.fromString("http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg")));
        assertEquals("image/gif", MyContentType.uri2MimeType(contentResolver, demoData.localGifTestUri));
        assertEquals("image/gif", MyContentType.uri2MimeType(contentResolver, demoData.localGifTestUri,
                MyContentType.UNKNOWN.generalMimeType));
        assertEquals("image/gif", MyContentType.uri2MimeType(contentResolver, demoData.localGifTestUri,
                IMAGE.generalMimeType));
        Uri uriTxt = Uri.parse("http://example.com/something_txt");
        assertEquals("image/*", MyContentType.uri2MimeType(contentResolver, uriTxt, IMAGE.generalMimeType));
        assertEquals("text/plain", MyContentType.uri2MimeType(contentResolver, uriTxt, TEXT.generalMimeType));
    }
}
