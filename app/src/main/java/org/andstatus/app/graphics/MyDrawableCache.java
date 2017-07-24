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

package org.andstatus.app.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yvolk@yurivolkov.com
 * On LruCache usage read http://developer.android.com/reference/android/util/LruCache.html
 */
public class MyDrawableCache extends LruCache<String, CachedDrawable> {

    public final static int BYTES_PER_PIXEL = 4;
    final String name;
    private volatile int requestedCacheSize;
    private volatile int currentCacheSize;
    private volatile int maxBitmapHeight;
    private volatile int maxBitmapWidth;
    final AtomicLong hits = new AtomicLong();
    final AtomicLong misses = new AtomicLong();
    final Set<String> brokenBitmaps = new ConcurrentSkipListSet<>();
    final Queue<Bitmap> recycledBitmaps;
    final DisplayMetrics displayMetrics;
    volatile boolean rounded = false;

    @Override
    public void resize(int maxSize) {
        throw new IllegalStateException("Cache cannot be resized");
    }

    public MyDrawableCache(Context context, String name, int maxBitmapHeightWidthIn, int requestedCacheSizeIn) {
        super(requestedCacheSizeIn);
        this.name = name;
        displayMetrics = context.getResources().getDisplayMetrics();
        int maxBitmapHeightWidth = maxBitmapHeightWidthIn;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            if (maxBitmapHeightWidth > displayMetrics.heightPixels) {
                maxBitmapHeightWidth = displayMetrics.heightPixels;
            }
            if (maxBitmapHeightWidth > displayMetrics.widthPixels) {
                maxBitmapHeightWidth = displayMetrics.widthPixels;
            }
        }
        this.setMaxBounds(maxBitmapHeightWidth, maxBitmapHeightWidth);
        this.requestedCacheSize = requestedCacheSizeIn;
        this.currentCacheSize = this.requestedCacheSize;
        recycledBitmaps = new ConcurrentLinkedQueue<>();
        try {
            for (int i = 0; i < currentCacheSize + 2; i++) {
                recycledBitmaps.add(newBlankBitmap());
            }
        } catch (OutOfMemoryError e) {
            MyLog.w(this, getInfo(), e);
            currentCacheSize = recycledBitmaps.size() - 2;
            if (currentCacheSize < 0) {
                currentCacheSize = 0;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                super.resize(currentCacheSize);
            }
        }
    }

    private Bitmap newBlankBitmap() {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            bitmap = Bitmap.createBitmap(displayMetrics, maxBitmapWidth,
                    maxBitmapHeight, CachedDrawable.BITMAP_CONFIG);
        } else {
            bitmap = Bitmap.createBitmap(maxBitmapWidth,
                    maxBitmapHeight, CachedDrawable.BITMAP_CONFIG);
            bitmap.setDensity(displayMetrics.densityDpi);
        }
        return bitmap;
    }

    @Nullable
    CachedDrawable getCachedDrawable(Object objTag, String path) {
        return getDrawable(objTag, path, true);
    }

    @Nullable
    CachedDrawable loadAndGetDrawable(Object objTag, String path) {
        return getDrawable(objTag, path, false);
    }

    @Override
    protected void entryRemoved(boolean evicted, String key, CachedDrawable oldValue, CachedDrawable newValue) {
        if (oldValue.isBitmapRecyclable()) {
            oldValue.makeExpired();
            recycledBitmaps.add(oldValue.getBitmap());
        }
    }

    @Nullable
    private CachedDrawable getDrawable(Object objTag, String path, boolean fromCacheOnly) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        CachedDrawable drawable = get(path);
        if (drawable != null) {
            hits.incrementAndGet();
        } else if (brokenBitmaps.contains(path)) {
            hits.incrementAndGet();
            return CachedDrawable.BROKEN;
        } else if (!(new File(path)).exists()) {
            misses.incrementAndGet();
        } else {
            misses.incrementAndGet();
            if (!fromCacheOnly) {
                drawable = loadDrawable(objTag, path);
                if (drawable != null) {
                    if (currentCacheSize > 0) {
                        put(path, drawable);
                    }
                } else {
                    brokenBitmaps.add(path);
                }
            }
        }
        return drawable;
    }

    @Nullable
    private CachedDrawable loadDrawable(Object objTag, String path) {
        Bitmap bitmap = loadBitmap(objTag, path);
        if (bitmap == null) {
            return null;
        }
        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Bitmap background = getSuitableRecycledBitmap(srcRect);
        if (background == null) {
            MyLog.w(objTag, "No suitable bitmap found to cache "
                    + srcRect.width() + "x" + srcRect.height() + " '" + path + "'");
            return null ;
        }
        Canvas canvas = new Canvas(background);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (rounded) {
            drawRoundedBitmap(canvas, bitmap);
        } else {
            canvas.drawBitmap(bitmap, 0 , 0, null);
        }
        bitmap.recycle();
        return new CachedDrawable(background, srcRect);
    }

    /**
     * The solution is from http://evel.io/2013/07/21/rounded-avatars-in-android/
     */
    private void drawRoundedBitmap(Canvas canvas, Bitmap bitmap) {
        RectF rectF = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        final BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        canvas.drawOval(rectF, paint);
    }

    private Bitmap getSuitableRecycledBitmap(Rect srcRect) {
        return recycledBitmaps.poll();
    }

    @Nullable
    private Bitmap loadBitmap(Object objTag, String path) {
        Bitmap bitmap = null;
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            bitmap = BitmapFactory
                    .decodeFile(path, calculateScaling(objTag, getImageSize(path)));
        } else {
            try {
                bitmap = BitmapFactory
                        .decodeFile(path, calculateScaling(objTag, getImageSize(path)));
            } catch (OutOfMemoryError e) {
                MyLog.w(objTag, getInfo(), e);
                evictAll();
            }
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, (bitmap == null ? "Failed to load " + name + "'s bitmap"
                    : "Loaded " + name + "'s bitmap " + bitmap.getWidth()
                    + "x" + bitmap.getHeight()) + " '" + path + "'");
        }
        return bitmap;
    }

    Point getImageSize(String path) {
        if (!TextUtils.isEmpty(path)) {
            CachedDrawable drawable = get(path);
            if (drawable != null) {
                return drawable.getImageSize();
            }
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                return new Point(options.outWidth, options.outHeight);
            } catch (Exception e) {
                MyLog.d("getImageSize", "path:'" + path + "'", e);
            }
        }
        return new Point(0, 0);
    }

    BitmapFactory.Options calculateScaling(Object objTag, Point imageSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int x = maxBitmapWidth;
        int y = maxBitmapHeight;
        while (imageSize.y > y || imageSize.x > x) {
            options.inSampleSize = (options.inSampleSize < 2) ? 2 : options.inSampleSize * 2;
            x *= 2;
            y *= 2;
        }
        if (options.inSampleSize > 1 && MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, "Large bitmap " + imageSize.x + "x" + imageSize.y
                    + " scaling by " + options.inSampleSize + " times");
        }
        return options;
    }

    public String getInfo() {
        StringBuilder builder = new StringBuilder(name);
        builder.append(": " + maxBitmapWidth + "x" + maxBitmapHeight + ", "
                + size() + " of " + currentCacheSize);
        if (requestedCacheSize != currentCacheSize) {
            builder.append(" (initially capacity was " + requestedCacheSize + ")");
        }
        builder.append(", free: " + recycledBitmaps.size());
        if (!brokenBitmaps.isEmpty()) {
            builder.append(", broken: " + brokenBitmaps.size());
        }
        long accesses = hits.get() + misses.get();
        builder.append(", hits:" + hits.get() + ", misses:" + misses.get()
                + (accesses == 0 ? "" : ", hitRate:" + hits.get() * 100 / accesses + "%"));
        return builder.toString();
    }

    public int getMaxBitmapWidth() {
        return maxBitmapWidth;
    }

    private final void setMaxBounds(int x, int y) {
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
