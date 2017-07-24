/*
 * Copyright (c) 2016-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public class CachedImage {
    public static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;
    public static final Rect EMPTY_RECT = new Rect(0, 0, 0, 0);
    public static final Bitmap EMPTY_BITMAP = newBitmap(1);
    public static final CachedImage EMPTY = new CachedImage(-1, EMPTY_BITMAP, EMPTY_RECT).makeExpired();
    public static final CachedImage BROKEN = new CachedImage(-2, EMPTY_BITMAP, EMPTY_RECT).makeExpired();
    public final long id;
    private final Bitmap bitmap;
    protected final Drawable source;
    private volatile boolean expired = false;

    public CachedImage(long imageId, @NonNull Bitmap bitmap, @NonNull Rect srcRect) {
        id = imageId;
        this.bitmap = bitmap;
        source = new BitmapSubsetDrawable(bitmap, srcRect);
    }

    public CachedImage(long imageId, @NonNull Drawable drawable) {
        id = imageId;
        bitmap = EMPTY_BITMAP;
        source = drawable;
    }

    private static Bitmap newBitmap(int size) {
        return Bitmap.createBitmap(size, size, BITMAP_CONFIG);
    }

    boolean isBitmapRecyclable() {
        return !expired && !EMPTY_BITMAP.equals(bitmap);
    }

    @NonNull
    Bitmap getBitmap() {
        return bitmap;
    }

    public boolean isExpired() {
        return expired;
    }

    CachedImage makeExpired() {
        this.expired = true;
        return this;
    }

    public Drawable getDrawable() {
        return source;
    }

    public Point getImageSize() {
        return new Point(source.getIntrinsicWidth(), source.getIntrinsicHeight());
    }
}
