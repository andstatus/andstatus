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

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.view.Display;
import android.view.WindowManager;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MyTheme;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class ImageCaches {
    private static final float ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_APP_MEMORY = 0.20f;
    public static final int ATTACHED_IMAGES_CACHE_SIZE_MIN = 10;
    public static final int ATTACHED_IMAGES_CACHE_SIZE_MAX = 20;
    private static final float AVATARS_CACHE_PART_OF_TOTAL_APP_MEMORY = 0.05f;
    public static final int AVATARS_CACHE_SIZE_MIN = 200;
    public static final int AVATARS_CACHE_SIZE_MAX = 700;

    private static volatile ImageCache attachedImagesCache;
    private static volatile ImageCache avatarsCache;

    private ImageCaches() {
        // Empty
    }

    public static synchronized void initialize(Context context) {
        styledImages.clear();
        if (attachedImagesCache != null) {
            return;
        }
        initializeAttachedImagesCache(context);
        initializeAvatarsCache(context);
        MyLog.i(ImageCaches.class.getSimpleName(), "Cache initialized. " + getCacheInfo());
    }

    private static void initializeAttachedImagesCache(Context context) {
        // We assume that current display orientation is preferred, so we use "y" size only
        int imageSize = (int) Math.round(AttachedImageView.MAX_ATTACHED_IMAGE_PART *
                getDisplaySize(context).y);
        int cacheSize = 0;
        for (int i = 0 ; i < 5; i++) {
            cacheSize = calcCacheSize(context, imageSize,
                    ATTACHED_IMAGES_CACHE_PART_OF_TOTAL_APP_MEMORY);
            if (cacheSize >= ATTACHED_IMAGES_CACHE_SIZE_MIN || imageSize < 2 ) {
                break;
            }
            imageSize = (imageSize * 2) / 3;
        }
        if (cacheSize > ATTACHED_IMAGES_CACHE_SIZE_MAX) {
            cacheSize = ATTACHED_IMAGES_CACHE_SIZE_MAX;
        }
        attachedImagesCache = new ImageCache(context, CacheName.ATTACHED_IMAGE, imageSize,
                cacheSize);
    }

    private static void initializeAvatarsCache(Context context) {
        float displayDensity = context.getResources().getDisplayMetrics().density;
        int imageSize = Math.round(AvatarFile.AVATAR_SIZE_DIP * displayDensity);
        int cacheSize = 0;
        for (int i = 0 ; i < 5; i++) {
            cacheSize = calcCacheSize(context, imageSize, AVATARS_CACHE_PART_OF_TOTAL_APP_MEMORY);
            if (cacheSize >= AVATARS_CACHE_SIZE_MIN || imageSize < 48 ) {
                break;
            }
            imageSize = (imageSize * 2) / 3;
        }
        if (cacheSize > AVATARS_CACHE_SIZE_MAX) {
            cacheSize = AVATARS_CACHE_SIZE_MAX;
        }
        avatarsCache = new ImageCache(context, CacheName.AVATAR, imageSize, cacheSize);
        setAvatarsRounded();
    }

    public static void setAvatarsRounded() {
        avatarsCache.evictAll();
        avatarsCache.rounded = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_ROUNDED_AVATARS, true);
    }

    private static int calcCacheSize(Context context, int imageSize, float partOfAvailableMemory) {
        return Math.round(partOfAvailableMemory * getTotalAppMemory(context)
                / imageSize / imageSize / ImageCache.BYTES_PER_PIXEL);
    }

    @NonNull
    private static long getTotalAppMemory(Context context) {
        int memoryClass = 16;
        if (context != null) {
            ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            memoryClass = actManager.getMemoryClass();
        }
        return memoryClass * 1024L * 1024L;
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
    public static Point getImageSize(CacheName cacheName, long imageId, String path) {
        return getCache(cacheName).getImageSize(imageId, path);
    }

    public static CachedImage loadAndGetImage(CacheName cacheName, Object objTag, long imageId, String path) {
        return getCache(cacheName).loadAndGetImage(objTag, imageId, path);
    }

    public static CachedImage getCachedImage(CacheName cacheName, Object objTag, long imageId, String path) {
        return getCache(cacheName).getCachedImage(objTag, imageId, path);
    }

    public static ImageCache getCache(CacheName cacheName) {
        switch (cacheName) {
            case ATTACHED_IMAGE:
                return attachedImagesCache;
            default:
                return avatarsCache;
        }
    }

    public static String getCacheInfo() {
        StringBuilder builder = new StringBuilder("ImageCaches: ");
        if (avatarsCache == null || attachedImagesCache == null) {
            builder.append("not initialized\n");
        } else {
            builder.append(avatarsCache.getInfo() + "\n");
            builder.append(attachedImagesCache.getInfo() + "\n");
            builder.append("Styled images: " + styledImages.size() + "\n");
        }
        Context context = MyContextHolder.get().context();
        if (context != null) {
            builder.append("Memory. App total: " + I18n.formatBytes(getTotalAppMemory(context)));
            ActivityManager.MemoryInfo memInfo = getMemoryInfo(context);
            builder.append("; Device: available " + I18n.formatBytes(memInfo.availMem) + " of "
                    + I18n.formatBytes(memInfo.totalMem) + "\n");
        }
        return builder.toString();
    }

    /**
     * See http://stackoverflow.com/questions/1016896/how-to-get-screen-dimensions
     */
    public static Point getDisplaySize(Context context) {
        Point size = new Point();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            Display display = windowManager.getDefaultDisplay();
            if (display != null) {
                display.getSize(size);
            }
        }
        if (size.x < 480 || size.y < 480) {
            // This is needed for preview in Layout editor
            size.x = 1280;
            size.y = 768;
        }
        return size;
    }

    public static CachedImage getImageCompat(Context context, int resourceId) {
        return new CachedImage(resourceId, context.getTheme().getDrawable(resourceId));
    }

    private static final Map<Integer, CachedImage[]> styledImages = new ConcurrentHashMap<>();
    public static CachedImage getStyledImage(int resourceIdLight, int resourceId) {
        CachedImage[] styledImage = styledImages.get(resourceId);
        if (styledImage == null) {
            Context context = MyContextHolder.get().context();
            if (context != null) {
                CachedImage image = getImageCompat(context, resourceId);
                CachedImage imageLight = getImageCompat(context, resourceIdLight);
                styledImage = new CachedImage[]{image, imageLight};
                styledImages.put(resourceId, styledImage);
            } else {
                return CachedImage.EMPTY;
            }
        }
        return styledImage[MyTheme.isThemeLight() ? 1 : 0];
    }

}
