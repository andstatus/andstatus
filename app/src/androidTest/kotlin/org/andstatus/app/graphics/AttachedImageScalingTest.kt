package org.andstatus.app.graphics

import android.graphics.Point
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class AttachedImageScalingTest {
    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testScaling() {
        ImageCaches.initialize( MyContextHolder.myContextHolder.getNow().context)
        val cache = ImageCaches.getCache(CacheName.ATTACHED_IMAGE)
        val exactlyMaxSize = Point(cache.getMaxBitmapWidth(), cache.getMaxBitmapWidth())
        var options = cache.calculateScaling(this, exactlyMaxSize)
        Assert.assertEquals(1, options.inSampleSize.toLong())
        val largerSize = Point(exactlyMaxSize.y + 10, exactlyMaxSize.x + 30)
        options = cache.calculateScaling(this, largerSize)
        Assert.assertEquals(2, options.inSampleSize.toLong())
        var imageSize = Point(largerSize.x * 2, largerSize.y * 2)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertEquals(4, options.inSampleSize.toLong())
        imageSize = Point(largerSize.x + largerSize.x / 2, largerSize.y + largerSize.y / 2)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertEquals(2, options.inSampleSize.toLong())
        imageSize = Point(largerSize.x * 3, largerSize.y)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertEquals(4, options.inSampleSize.toLong())
        imageSize = Point(largerSize.x, largerSize.y * 3)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertEquals(4, options.inSampleSize.toLong())
        imageSize = Point(largerSize.x / 2, largerSize.y)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertEquals(2, options.inSampleSize.toLong())
        imageSize = Point(largerSize.x / 2, largerSize.y / 2)
        options = cache.calculateScaling(this, imageSize)
        Assert.assertTrue(options.inSampleSize < 2)
    }
}
