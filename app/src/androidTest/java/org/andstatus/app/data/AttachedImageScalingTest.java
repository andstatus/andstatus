package org.andstatus.app.data;

import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;

public class AttachedImageScalingTest extends InstrumentationTestCase {
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }
    
    public void testScaling() {
        MyImageCache.initialize(MyContextHolder.get().context());
        Point display = AttachedImageDrawable.getDisplaySize(MyContextHolder.get().context());
        MyBitmapCache cache = MyImageCache.attachedImagesCache;
        BitmapFactory.Options options = cache.calculateScaling(this, display);
        assertEquals(2, options.inSampleSize);
        Point imageSize = new Point(display.x * 2, display.y * 2);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(display.x + display.x / 2, display.y + display.y / 2);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(2, options.inSampleSize);
        imageSize = new Point(display.x * 3, display.y);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(display.x, display.y * 3);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(4, options.inSampleSize);
        imageSize = new Point(display.x / 2, display.y);
        options = cache.calculateScaling(this, imageSize);
        assertEquals(2, options.inSampleSize);
        imageSize = new Point(display.x, display.y / 2);
        options = cache.calculateScaling(this, imageSize);
        assertTrue(options.inSampleSize < 2);
    }

}
