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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Display;
import android.view.WindowManager;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyTheme;
import org.andstatus.app.util.I18n;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final int ATTACHED_IMAGES_CACHE_SIZE_MINIMUM = 1024 * 1024 * 20;
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_AVAILABLE_MEMORY = 0.1f;
    static final MyDrawableCache attachedImagesCache =
            new MyDrawableCache("Attached images", 5000, ATTACHED_IMAGES_CACHE_SIZE_MINIMUM);

    private static final float AVATARS_CACHE_PART_OF_ATTACHED = 0.2f;
    private static final MyDrawableCache avatarsCache =
            new MyDrawableCache("Avatars", 1000,
                    Math.round(ATTACHED_IMAGES_CACHE_SIZE_MINIMUM * AVATARS_CACHE_PART_OF_ATTACHED));

    private  MyImageCache() {
        // Empty
    }

    public static void initialize(Context context) {
        attachedImagesCache.evictAll();
        int attachedImageCacheSize = calcAttachedImagesCacheSize(context);
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

        styledDrawables.clear();
    }

    private static int calcAttachedImagesCacheSize(Context context) {
        ActivityManager.MemoryInfo memInfo = getMemoryInfo(context);
        int size = Math.round(
                ATTACHED_IMAGES_CACHE_PART_OF_AVAILABLE_MEMORY * memInfo.availMem);
        if (size < ATTACHED_IMAGES_CACHE_SIZE_MINIMUM) {
            size = 0;
        }
        return size;
    }

    @NonNull
    private static ActivityManager.MemoryInfo getMemoryInfo(Context context) {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        if (context != null) {
            ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            actManager.getMemoryInfo(memInfo);
        }
        return memInfo;
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

    public static Drawable getCachedAttachedImageDrawable(Object objTag, String path) {
        return attachedImagesCache.getCachedDrawable(objTag, path);
    }

    public static Drawable getAttachedImageDrawable(Object objTag, String path) {
        return attachedImagesCache.getDrawable(objTag, path);
    }

    public static String getCacheInfo() {
        StringBuilder builder = new StringBuilder("ImageCaches:\n");
        builder.append(avatarsCache.getInfo() + "\n");
        builder.append(attachedImagesCache.getInfo() + "\n");
        builder.append("Styled drawables: " + styledDrawables.size() + "\n");
        ActivityManager.MemoryInfo memInfo = getMemoryInfo(MyContextHolder.get().context());
        builder.append("Memory: available " + I18n.formatBytes(memInfo.availMem) + " of " + I18n.formatBytes(memInfo.totalMem) + "\n");
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Drawable getDrawableCompat(Context context, int drawableId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)  {
            return context.getTheme().getDrawable(drawableId);
        } else {
            return context.getResources().getDrawable(drawableId);
        }
    }

    private static final Map<Integer, Drawable[]> styledDrawables = new ConcurrentHashMap<>();
    public static Drawable getStyledDrawable(int resourceIdLight, int resourceId) {
        Drawable[] styledDrawable = styledDrawables.get(resourceId);
        if (styledDrawable == null) {
            Context context = MyContextHolder.get().context();
            if (context != null) {
                Drawable drawable = getDrawableCompat(context, resourceId);
                Drawable drawableLight = getDrawableCompat(context, resourceIdLight);
                styledDrawable = new Drawable[]{drawable, drawableLight};
                styledDrawables.put(resourceId, styledDrawable);
            } else {
                return new BitmapDrawable();
            }
        }
        return styledDrawable[MyTheme.isThemeLight() ? 1 : 0];
    }

}
