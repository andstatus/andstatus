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
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.graphics.MyDrawableCache;
import org.andstatus.app.util.I18n;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyImageCache {
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_AVAILABLE_MEMORY = 0.1f;
    public static final int ATTACHED_IMAGES_CACHE_SIZE_MAX = 50;
    private static final float AVATARS_CACHE_PART_OF_ATTACHED = 0.1f;
    public static final int AVATARS_CACHE_SIZE_MAX = 700;
    static volatile MyDrawableCache attachedImagesCache;
    private static volatile MyDrawableCache avatarsCache;

    private  MyImageCache() {
        // Empty
    }

    public static synchronized void initialize(Context context) {
        styledDrawables.clear();
        if (attachedImagesCache != null) {
            return;
        }
        Point displaySize = getDisplaySize(context);
        float displayDensity = context.getResources().getDisplayMetrics().density;
        int attachedImageSize = (int) Math.round(AttachedImageFile.MAX_ATTACHED_IMAGE_PART *
                (displaySize.x > displaySize.y ? displaySize.y : displaySize.x) / displayDensity);
        int attachedImageCacheSize = calcCacheSize(context, attachedImageSize,
                ATTACHED_IMAGES_CACHE_PART_OF_AVAILABLE_MEMORY);
        if (attachedImageCacheSize > ATTACHED_IMAGES_CACHE_SIZE_MAX) {
            attachedImageCacheSize = ATTACHED_IMAGES_CACHE_SIZE_MAX;
        }
        attachedImagesCache = new MyDrawableCache(context, "Attached images", attachedImageSize,
                attachedImageCacheSize);

        int avatarSize = AvatarFile.AVATAR_SIZE_DIP;
        int avatarsCacheSize = calcCacheSize(context, avatarSize,
                AVATARS_CACHE_PART_OF_ATTACHED * ATTACHED_IMAGES_CACHE_PART_OF_AVAILABLE_MEMORY);
        if (avatarsCacheSize > AVATARS_CACHE_SIZE_MAX) {
            avatarsCacheSize = AVATARS_CACHE_SIZE_MAX;
        }
        avatarsCache = new MyDrawableCache(context, "Avatars", avatarSize, avatarsCacheSize);
    }

    private static int calcCacheSize(Context context, int imageSize, float partOfAvailableMemory) {
        ActivityManager.MemoryInfo memInfo = getMemoryInfo(context);
        int size = Math.round(
                partOfAvailableMemory * memInfo.availMem
                        / imageSize / imageSize / MyDrawableCache.BYTES_PER_PIXEL);
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
    public static Point getAttachedImageSize(String path) {
        return attachedImagesCache.getImageSize(path);
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
