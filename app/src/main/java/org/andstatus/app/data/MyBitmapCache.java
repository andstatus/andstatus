/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.util.LruCache;

import org.andstatus.app.util.MyLog;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yvolk@yurivolkov.com
 * On LruCache usage read http://developer.android.com/reference/android/util/LruCache.html
 */
public class MyBitmapCache extends LruCache<String, Bitmap> {
    final String name;
    private volatile int maxCacheSize;
    private volatile int maxBitmapHeight;
    private volatile int maxBitmapWidth;
    final AtomicLong hits = new AtomicLong();
    final AtomicLong misses = new AtomicLong();

    @Override
    public void resize(int maxSize) {
        maxCacheSize = maxSize;
        super.resize(maxSize);
    }

    public MyBitmapCache(String name, int maxBitmapHeightWidth, int maxCacheSize) {
        super(maxCacheSize);
        this.name = name;
        this.setMaxBounds(maxBitmapHeightWidth, maxBitmapHeightWidth);
        this.maxCacheSize = maxCacheSize;
    }

    @Override
    protected int sizeOf(String key, Bitmap value) {
        return value.getByteCount();
    }

    Bitmap getBitmap(Object objTag, String path, Point imageSize) {
        Bitmap bitmap = get(path);
        if (bitmap != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
            bitmap = BitmapFactory
                    .decodeFile(path, calculateScaling(objTag, imageSize));
            if (bitmap != null) {
                put(path, bitmap);
            }
        }
        return bitmap;
    }

    BitmapFactory.Options calculateScaling(Object objTag, Point imageSize) {
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        int x = maxBitmapWidth;
        int y = maxBitmapHeight;
        while (imageSize.y > y || imageSize.x > x) {
            options2.inSampleSize = (options2.inSampleSize < 2) ? 2 : options2.inSampleSize * 2;
            x *= 2;
            y *= 2;
        }
        if (options2.inSampleSize > 1 && MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, "Large bitmap " + imageSize.x + "x" + imageSize.y
                    + " scaling by " + options2.inSampleSize + " times");
        }
        return options2;
    }

    public String getInfo() {
        StringBuilder builder = new StringBuilder(name);
        builder.append(" size:" + size() + " of " + maxCacheSize + " bytes");
        long accesses = hits.get() + misses.get();
        builder.append(", hits:" + hits.get() + ", misses:" + misses.get()
                + (accesses == 0 ? "" : ", hitRate:" + hits.get() * 100 / accesses + "%"));
        return builder.toString();
    }

    public int getMaxBitmapWidth() {
        return maxBitmapWidth;
    }

    public final void setMaxBounds(int x, int y) {
        if ( x < 1 || y < 1) {
            MyLog.e(this, MyLog.getStackTrace(
                    new IllegalArgumentException("setMaxBounds x=" + x + " y=" + y))
            );
        } else {
            maxBitmapWidth = x;
            maxBitmapHeight = y;
        }
    }
}
