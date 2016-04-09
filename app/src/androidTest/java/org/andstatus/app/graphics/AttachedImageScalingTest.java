package org.andstatus.app.graphics;

import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.graphics.MyDrawableCache;
import org.andstatus.app.graphics.MyImageCache;

public class AttachedImageScalingTest extends InstrumentationTestCase {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }
    
    public void testScaling() {
        MyImageCache.initialize(MyContextHolder.get().context());
        MyDrawableCache cache = MyImageCache.attachedImagesCache;
        Point exactlyMaxSize = new Point(cache.getMaxBitmapWidth(), cache.getMaxBitmapWidth());
        BitmapFactory.Options options = cache.calculateScaling(this, exactlyMaxSize);
        assertEquals(0, options.inSampleSize);
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
