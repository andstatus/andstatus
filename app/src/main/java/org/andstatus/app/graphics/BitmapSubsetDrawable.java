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
public class BitmapSubsetDrawable extends Drawable {
    private Bitmap bitmap;
    private Rect scrRect;

    public BitmapSubsetDrawable(@NonNull Bitmap bitmap, @NonNull Rect srcRect) {
        this.bitmap = bitmap;
        this.scrRect = srcRect;
    }

    @Override
    public int getIntrinsicWidth() {
        return scrRect.width();
    }

    @Override
    public int getIntrinsicHeight() {
        return scrRect.height();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(bitmap, scrRect, getBounds(), null);
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

    public Bitmap getBitmap() {
        return bitmap;
    }
}
