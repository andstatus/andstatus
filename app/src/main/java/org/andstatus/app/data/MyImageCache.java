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

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.LruCache;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final LruCache<String, Point> boundsCache = new LruCache<>(1000);
    private static final MyBitmapCache avatarsCache = new MyBitmapCache("Avatars", 500, 1024 * 1024);
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_MEMORY = 0.1f;
    static final MyBitmapCache attachedImagesCache = new MyBitmapCache("Attached images", 3000, 1024 * 1024 * 40);

    private  MyImageCache() {
        // Empty
    }

    public static void initialize(Context context) {
        attachedImagesCache.evictAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            attachedImagesCache.resize(Math.round(
                    ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_MEMORY * getTotalMemory(context)
            ));
        }
        Point displaySize = AttachedImageDrawable.getDisplaySize(context);
        attachedImagesCache.setMaxBounds(displaySize.x,
                (int) (AttachedImageDrawable.MAX_ATTACHED_IMAGE_PART * displaySize.y));

        avatarsCache.evictAll();
        float displayDensity = context.getResources().getDisplayMetrics().density;
        int avatarSize = Math.round(AvatarDrawable.AVATAR_SIZE_DIP * displayDensity);
        avatarsCache.setMaxBounds(avatarSize, avatarSize);
    }

    private static long getTotalMemory(Context context) {
        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo.totalMem;
    }

    public static Point getImageSize(String path) {
        if (TextUtils.isEmpty(path)) {
            return new Point(0, 0);
        }
        Point bounds = boundsCache.get(path);
        if (bounds == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            bounds = new Point(options.outWidth, options.outHeight);
            boundsCache.put(path, bounds);
        }
        return bounds;
    }

    @Nullable
    public static Drawable getAvatarDrawable(Object objTag, String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        Point imageSize = getImageSize(path);
        Bitmap bitmap = avatarsCache.getBitmap(objTag, path, imageSize);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, (bitmap == null ? "Failed to load avatar's bitmap"
                    : "Loaded avatar's bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight())
                    + " '" + path + "'");
        }
        if (bitmap == null) {
            return null;
        }
        return new BitmapDrawable(MyContextHolder.get().context().getResources(), bitmap);
    }

    public static int getAvatarWidthPixels() {
        return avatarsCache.getMaxBitmapWidth();
    }

    public static Drawable getAttachedImageDrawable(Object objTag, String path, Point imageSize) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        Bitmap bitmap = attachedImagesCache.getBitmap(objTag, path, imageSize);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, (bitmap == null ? "Failed to load bitmap" : "Loaded bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight())
                    + " '" + path + "'");
        }
        if (bitmap == null) {
            return null;
        }
        return new BitmapDrawable(MyContextHolder.get().context().getResources(), bitmap);
    }

    public static String getCacheInfo() {
        StringBuilder builder = new StringBuilder("ImageCaches:\n");
        builder.append("Bounds size:" + boundsCache.size() + " items, " + boundsCache.toString() + "\n");
        builder.append(avatarsCache.getInfo() + "\n");
        builder.append(attachedImagesCache.getInfo() + "\n");
        return builder.toString();
    }
}
