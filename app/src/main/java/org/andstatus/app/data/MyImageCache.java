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

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final MyDrawableCache avatarsCache =
            new MyDrawableCache("Avatars", 1000, 1024 * 1024);
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_MEMORY = 0.1f;
    static final MyDrawableCache attachedImagesCache =
            new MyDrawableCache("Attached images", 5000, 1024 * 1024 * 40);

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
}
