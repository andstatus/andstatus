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
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.LruCache;

import androidx.annotation.Nullable;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MediaFile;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.io.File;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 * On LruCache usage read http://developer.android.com/reference/android/util/LruCache.html
 */
public class ImageCache extends LruCache<String, CachedImage> {
    static final String TAG = ImageCache.class.getSimpleName();

    public final static int BYTES_PER_PIXEL = 4;
    final CacheName name;
    private volatile int requestedCacheSize;
    private volatile int currentCacheSize;
    volatile int maxBitmapHeight;
    volatile int maxBitmapWidth;
    final AtomicLong hits = new AtomicLong();
    final AtomicLong misses = new AtomicLong();
    final Set<String> brokenBitmaps = new ConcurrentSkipListSet<>();
    final Queue<Bitmap> recycledBitmaps;
    final DisplayMetrics displayMetrics;
    volatile boolean rounded = false;
    final boolean showImageInimations;

    @Override
    public void resize(int maxSize) {
        throw new IllegalStateException("Cache cannot be resized");
    }

    public ImageCache(Context context, CacheName name, int maxBitmapHeightWidthIn, int requestedCacheSizeIn) {
        super(requestedCacheSizeIn);
        showImageInimations = MyPreferences.isShowImageAnimations();
        this.name = name;
        displayMetrics = context.getResources().getDisplayMetrics();
        int maxBitmapHeightWidth = maxBitmapHeightWidthIn;
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
            super.resize(currentCacheSize);
        }
    }

    private Bitmap newBlankBitmap() {
        return Bitmap.createBitmap(displayMetrics, maxBitmapWidth,
                maxBitmapHeight, CachedImage.BITMAP_CONFIG);
    }

    @Nullable
    CachedImage getCachedImage(MediaFile mediaFile) {
        return getImage(mediaFile, true);
    }

    @Nullable
    CachedImage loadAndGetImage(MediaFile mediaFile) {
        return getImage(mediaFile, false);
    }

    @Override
    protected void entryRemoved(boolean evicted, String key, CachedImage oldValue, CachedImage newValue) {
        if (oldValue.isBitmapRecyclable()) {
            oldValue.makeExpired();
            recycledBitmaps.add(oldValue.getBitmap());
        }
    }

    @Nullable
    private CachedImage getImage(MediaFile mediaFile, boolean fromCacheOnly) {
        if (StringUtil.isEmpty(mediaFile.getPath())) {
            return null;
        }
        CachedImage image = get(mediaFile.getPath());
        if (image != null) {
            hits.incrementAndGet();
        } else if (brokenBitmaps.contains(mediaFile.getPath())) {
            hits.incrementAndGet();
            return CachedImage.BROKEN;
        } else {
            misses.incrementAndGet();
            if (!fromCacheOnly && (new File(mediaFile.getPath())).exists()) {
                image = loadImage(mediaFile);
                if (image != null) {
                    if (currentCacheSize > 0) {
                        put(mediaFile.getPath(), image);
                    }
                } else {
                    brokenBitmaps.add(mediaFile.getPath());
                }
            }
        }
        return image;
    }

    @Nullable
    private CachedImage loadImage(MediaFile mediaFile) {
        switch (MyContentType.fromPathOfSavedFile(mediaFile.getPath())) {
            case IMAGE:
            case ANIMATED_IMAGE:
                if (showImageInimations && Build.VERSION.SDK_INT >= 28) {
                    return ImageCacheApi28Helper.animatedFileToCachedImage(this, mediaFile);
                }
                return imageFileToCachedImage(mediaFile);
            case VIDEO:
                return bitmapToCachedImage(mediaFile, videoFileToBitmap(mediaFile));
            default:
                return null;
        }
    }

    CachedImage imageFileToCachedImage(MediaFile mediaFile) {
        return bitmapToCachedImage(mediaFile, imageFileToBitmap(mediaFile));
    }

    CachedImage bitmapToCachedImage(MediaFile mediaFile, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        Rect srcRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        Bitmap background = getSuitableRecycledBitmap(srcRect);
        if (background == null) {
            MyLog.w(mediaFile, "No suitable bitmap found to cache "
                    + srcRect.width() + "x" + srcRect.height() + " '" + mediaFile.getPath() + "'");
            return null ;
        }
        Canvas canvas = new Canvas(background);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        try {
            // On Android 8+ this may cause
            //   java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
            // See https://stackoverflow.com/questions/58314397/java-lang-illegalstateexception-software-rendering-doesnt-support-hardware-bit
            if (rounded) {
                drawRoundedBitmap(canvas, bitmap);
            } else {
                canvas.drawBitmap(bitmap, 0 , 0, null);
            }
            bitmap.recycle();
        } catch (Exception e) {
            // TODO: better approach needed... maybe fail?!
            MyLog.w(TAG, "Drawing bitmap of " + mediaFile, e);
            recycledBitmaps.add(background);
            background = bitmap;
        }
        return new CachedImage(mediaFile.getId(), background, srcRect);
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
    private Bitmap imageFileToBitmap(MediaFile mediaFile) {
        try {
            final Bitmap bitmap;
            final BitmapFactory.Options options = calculateScaling(mediaFile, mediaFile.getSize());
            if (MyPreferences.isShowDebuggingInfoInUi()) {
                bitmap = BitmapFactory.decodeFile(mediaFile.getPath(), options);
            } else {
                try {
                    bitmap = BitmapFactory.decodeFile(mediaFile.getPath(), options);
                } catch (OutOfMemoryError e) {
                    MyLog.w(mediaFile, getInfo(), e);
                    evictAll();
                    return null;
                }
            }
            MyLog.v(mediaFile, () -> (bitmap == null ? "Failed to load " + name + "'s bitmap"
                    : "Loaded " + name + "'s bitmap " + bitmap.getWidth()
                    + "x" + bitmap.getHeight()) + " '" + mediaFile.getPath() + "' inSampleSize:" + options.inSampleSize);
            return bitmap;
        } catch (Exception e) {
            MyLog.w(this, "Error loading '" + mediaFile.getPath() + "'", e);
            return null;
        }
    }

    @Nullable
    private Bitmap videoFileToBitmap(MediaFile mediaFile) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(myContextHolder.getNow().context(), Uri.parse(mediaFile.getPath()));
            Bitmap source = retriever.getFrameAtTime();
            if (source == null) {
                return null;
            }
            BitmapFactory.Options options = calculateScaling(mediaFile, mediaFile.getSize());
            Bitmap bitmap = ThumbnailUtils.extractThumbnail(source, mediaFile.getSize().x / options.inSampleSize,
                    mediaFile.getSize().y / options.inSampleSize);
            source.recycle();
            MyLog.v(mediaFile,  () -> (bitmap == null ? "Failed to load " + name + "'s bitmap"
                    : "Loaded " + name + "'s bitmap " + bitmap.getWidth()
                    + "x" + bitmap.getHeight()) + " '" + mediaFile.getPath() + "'");
            return bitmap;
        } catch (Exception e) {
            MyLog.w(this, "Error loading '" + mediaFile.getPath() + "'", e);
            return null;
        } finally {
            DbUtils.closeSilently(retriever);
        }
    }

    BitmapFactory.Options calculateScaling(Object objTag, Point imageSize) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
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
        StringBuilder builder = new StringBuilder(name.title);
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
