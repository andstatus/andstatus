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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final LruCache<String, Point> boundsCash = new LruCache<>(1000);
    // On usage read http://developer.android.com/reference/android/util/LruCache.html
    private static final LruCache<String, Bitmap> bitmapCache = new LruCache(1024 * 1024 * 4) {
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }};

    public static Point getImageSize(String path) {
        Point bounds = boundsCash.get(path);
        if (bounds == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            bounds = new Point(options.outWidth, options.outHeight);
            boundsCash.put(path, bounds);
        }
        return bounds;
    }

    static Bitmap getBitmap(Object objTag, String path, Point imageSize) {
        Bitmap bitmap = bitmapCache.get(path);
        if (bitmap == null) {
            bitmap = BitmapFactory
                    .decodeFile(path, calculateScaling(objTag, imageSize));
            if (bitmap != null) {
                bitmapCache.put(path, bitmap);
            }
        }
        return bitmap;
    }

    static BitmapFactory.Options calculateScaling(Object objTag,
            Point imageSize) {
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        Point displaySize = AttachedImageDrawable.getDisplaySize(MyContextHolder.get().context());
        while (imageSize.y > (int) (AttachedImageDrawable.MAX_ATTACHED_IMAGE_PART * displaySize.y) || imageSize.x > displaySize.x) {
            options2.inSampleSize = (options2.inSampleSize < 2) ? 2 : options2.inSampleSize * 2;
            displaySize.set(displaySize.x * 2, displaySize.y * 2);
        }
        if (options2.inSampleSize > 1 && MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, "Large bitmap " + imageSize.x + "x" + imageSize.y
                    + " scaling by " + options2.inSampleSize + " times");
        }
        return options2;
    }
}
