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
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Display;
import android.view.WindowManager;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final int ATTACHED_IMAGES_CACHE_SIZE_DEFAULT = 1024 * 1024 * 40;
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_MEMORY = 0.1f;
    static final MyDrawableCache attachedImagesCache =
            new MyDrawableCache("Attached images", 5000, ATTACHED_IMAGES_CACHE_SIZE_DEFAULT);

    private static final float AVATARS_CACHE_PART_OF_ATTACHED = 0.2f;
    private static final MyDrawableCache avatarsCache =
            new MyDrawableCache("Avatars", 1000,
                    Math.round(ATTACHED_IMAGES_CACHE_SIZE_DEFAULT * AVATARS_CACHE_PART_OF_ATTACHED));

    private  MyImageCache() {
        // Empty
    }

    public static void initialize(Context context) {
        attachedImagesCache.evictAll();
        int attachedImageCacheSize = Math.round(
                ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_MEMORY * getTotalMemory(context));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            attachedImagesCache.resize(attachedImageCacheSize);
        }
        Point displaySize = getDisplaySize(context);
        attachedImagesCache.setMaxBounds(displaySize.x,
                (int) (AttachedImageFile.MAX_ATTACHED_IMAGE_PART * displaySize.y));

        avatarsCache.evictAll();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int avatarsCacheSize = Math.round(
                    AVATARS_CACHE_PART_OF_ATTACHED * attachedImageCacheSize);
            avatarsCache.resize(avatarsCacheSize);
        }
        float displayDensity = context.getResources().getDisplayMetrics().density;
        int avatarSize = Math.round(AvatarFile.AVATAR_SIZE_DIP * displayDensity);
        avatarsCache.setMaxBounds(avatarSize, avatarSize);
    }

    private static long getTotalMemory(Context context) {
        ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo.totalMem;
    }

    @NonNull
    public static Point getImageSize(String path) {
        return MyDrawableCache.getImageSize(path);
    }

    @Nullable
    public static Drawable getAvatarDrawable(Object objTag, String path) {
        return avatarsCache.getDrawable(objTag, path);
    }

    public static int getAvatarWidthPixels() {
        return avatarsCache.getMaxBitmapWidth();
    }

    public static Drawable getAttachedImageDrawable(Object objTag, String path) {
        return attachedImagesCache.getDrawable(objTag, path);
    }

    public static String getCacheInfo() {
        StringBuilder builder = new StringBuilder("ImageCaches:\n");
        builder.append(avatarsCache.getInfo() + "\n");
        builder.append(attachedImagesCache.getInfo() + "\n");
        return builder.toString();
    }

    /**
     * See http://stackoverflow.com/questions/1016896/how-to-get-screen-dimensions
     */
    public static Point getDisplaySize(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }
}
