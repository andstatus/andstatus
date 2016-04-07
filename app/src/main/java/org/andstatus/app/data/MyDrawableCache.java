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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;

import org.andstatus.app.context.MyContextHolder;
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
public class MyDrawableCache extends LruCache<String, Bitmap> {
    public final static BitmapDrawable BROKEN = new BitmapDrawable();
    public final static Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;
    public final static int BYTES_PER_PIXEL = 4;
    final String name;
    private volatile int initialCacheSize;
    private volatile int currentCacheSize;
    private volatile int maxBitmapHeight;
    private volatile int maxBitmapWidth;
    final AtomicLong hits = new AtomicLong();
    final AtomicLong misses = new AtomicLong();
    final Set<String> brokenBitmaps = new ConcurrentSkipListSet<>();
    final Queue<Bitmap> recycledBitmaps;
    final DisplayMetrics displayMetrics;

    @Override
    public void resize(int maxSize) {
        throw new IllegalStateException("Cache cannot be resized");
    }

    public MyDrawableCache(Context context, String name, int maxBitmapHeightWidth, int initialCacheSize) {
        super(initialCacheSize);
        this.name = name;
        this.setMaxBounds(maxBitmapHeightWidth, maxBitmapHeightWidth);
        this.initialCacheSize = initialCacheSize;
        this.currentCacheSize = initialCacheSize;
        recycledBitmaps = new ConcurrentLinkedQueue<>();
        displayMetrics = context.getResources().getDisplayMetrics();
        for (int i = 0; i < currentCacheSize + 2; i++) {
            recycledBitmaps.add(newBlankBitmap());
        }
    }

    private Bitmap newBlankBitmap() {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            bitmap = Bitmap.createBitmap(displayMetrics, maxBitmapWidth,
                    maxBitmapHeight, BITMAP_CONFIG);
        } else {
            bitmap = Bitmap.createBitmap(maxBitmapWidth,
                    maxBitmapHeight, BITMAP_CONFIG);
            bitmap.setDensity(displayMetrics.densityDpi);
        }
        return bitmap;
    }

    @Nullable
    BitmapDrawable getCachedDrawable(Object objTag, String path) {
        return getDrawable(objTag, path, true);
    }

    @Nullable
    BitmapDrawable getDrawable(Object objTag, String path) {
        return getDrawable(objTag, path, false);
    }

    @Override
    protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
        recycledBitmaps.add(oldValue);
    }

    @Nullable
    private BitmapDrawable getDrawable(Object objTag, String path, boolean fromCacheOnly) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        Bitmap bitmap = get(path);
        if (bitmap != null) {
            hits.incrementAndGet();
        } else if (brokenBitmaps.contains(path)) {
            hits.incrementAndGet();
            return BROKEN;
        } else if (!(new File(path)).exists()) {
            misses.incrementAndGet();
        } else {
            misses.incrementAndGet();
            if (!fromCacheOnly) {
                bitmap = loadBitmap(objTag, path);
                if (bitmap != null) {
                    if (currentCacheSize > 0) {
                        put(path, bitmap);
                    }
                } else {
                    brokenBitmaps.add(path);
                }
            }
        }
        return bitmap == null ? null :
                new BitmapDrawable(MyContextHolder.get().context().getResources(), bitmap);
    }

    @Nullable
    private Bitmap loadBitmap(Object objTag, String path) {
        Bitmap bitmapOriginal = loadBitmapFromFile(objTag, path);
        if (bitmapOriginal == null) {
            return null;
        }
        Bitmap background = getSuitableRecycledBitmap(bitmapOriginal);
        if (background != null) {
            Canvas canvas = new Canvas(background);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Matrix matrix = new Matrix();
            matrix.setRectToRect(new RectF(0, 0, bitmapOriginal.getWidth(), bitmapOriginal.getHeight()),
                    new RectF(0, 0, maxBitmapWidth, maxBitmapHeight), Matrix.ScaleToFit.CENTER);
            canvas.setMatrix(matrix);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setDither(true);
            canvas.drawBitmap(bitmapOriginal, 0 , 0, paint);
        }
        bitmapOriginal.recycle();
        return background;
    }

    private Bitmap getSuitableRecycledBitmap(Bitmap bitmapOriginal) {
        return recycledBitmaps.poll();
    }

    @Nullable
    private Bitmap loadBitmapFromFile(Object objTag, String path) {
        Bitmap bitmap = null;
        if (MyPreferences.showDebuggingInfoInUi()) {
            bitmap = BitmapFactory
                    .decodeFile(path, calculateScaling(objTag, getImageSize(path)));
        } else {
            try {
                bitmap = BitmapFactory
                        .decodeFile(path, calculateScaling(objTag, getImageSize(path)));
            } catch (OutOfMemoryError e) {
                MyLog.w(objTag, getInfo(), e);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    currentCacheSize /= 2;
                    super.resize(currentCacheSize);
                }
                evictAll();
                System.gc();
            }
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, (bitmap == null ? "Failed to load " + name + "'s bitmap"
                    : "Loaded " + name + "'s bitmap " + bitmap.getWidth()
                    + "x" + bitmap.getHeight()) + " '" + path + "'");
        }
        return bitmap;
    }

    public static Point getImageSize(String path) {
        if (!TextUtils.isEmpty(path)) {
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
        builder.append(" size: " + size() + " of " + currentCacheSize);
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
