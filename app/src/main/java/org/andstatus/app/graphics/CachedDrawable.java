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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public class CachedDrawable extends Drawable {
    public static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;
    public static final Rect EMPTY_RECT = new Rect(0, 0, 0, 0);
    public static final Bitmap EMPTY_BITMAP = newBitmap(1);
    public static final CachedDrawable EMPTY = new CachedDrawable(EMPTY_BITMAP, EMPTY_RECT).makeExpired();
    public static final CachedDrawable BROKEN = new CachedDrawable(EMPTY_BITMAP, EMPTY_RECT).makeExpired();
    private final Bitmap bitmap;
    private final Rect scrRect;
    private volatile boolean expired = false;

    public CachedDrawable(@NonNull Bitmap bitmap, @NonNull Rect srcRect) {
        this.bitmap = bitmap;
        this.scrRect = srcRect;
    }

    private static Bitmap newBitmap(int size) {
        return Bitmap.createBitmap(size, size, BITMAP_CONFIG);
    }

    @Override
    public int getIntrinsicWidth() {
        return getScrRect().width();
    }

    @Override
    public int getIntrinsicHeight() {
        return getScrRect().height();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(getBitmap(), getScrRect(), getBounds(), null);
    }

    @Override
    public void setAlpha(int alpha) {
        // Empty
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // Empty
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    boolean isBitmapRecyclable() {
        return !expired && bitmap.getHeight() > 0;
    }

    Bitmap getBitmap() {
        return expired ? EMPTY_BITMAP : bitmap;
    }

    private Rect getScrRect() {
        return expired ? EMPTY_RECT : scrRect;
    }

    public boolean isExpired() {
        return expired;
    }

    CachedDrawable makeExpired() {
        this.expired = true;
        return this;
    }

    public Drawable getDrawable() {
        return this;
    }
}
