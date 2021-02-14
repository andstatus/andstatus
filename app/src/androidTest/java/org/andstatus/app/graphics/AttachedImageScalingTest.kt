package org.andstatus.app.graphics;

import android.graphics.BitmapFactory;
import android.graphics.Point;

import org.andstatus.app.context.TestSuite;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AttachedImageScalingTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void testScaling() {
        ImageCaches.initialize(myContextHolder.getNow().context());
        ImageCache cache = ImageCaches.getCache(CacheName.ATTACHED_IMAGE);
        Point exactlyMaxSize = new Point(cache.getMaxBitmapWidth(), cache.getMaxBitmapWidth());
        BitmapFactory.Options options = cache.calculateScaling(this, exactlyMaxSize);
        assertEquals(1, options.inSampleSize);
        Point largerSize = new Point(exactlyMaxSize.y + 10, exactlyMaxSize.x + 30);
        options = cache.calculateScaling(this, largerSize);
        assertEquals(2, options.inSampleSize);
        Point imageSize = new Point(largerSize.x * 2, largerSize.y * 2);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(largerSize.x + largerSize.x / 2, largerSize.y + largerSize.y / 2);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(2, options.inSampleSize);
        imageSize = new Point(largerSize.x * 3, largerSize.y);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(largerSize.x, largerSize.y * 3);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(largerSize.x / 2, largerSize.y);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(2, options.inSampleSize);
        imageSize = new Point(largerSize.x / 2, largerSize.y / 2);
        options = cache.calculateScaling(this, imageSize);
        assertTrue(options.inSampleSize < 2);
    }

}
