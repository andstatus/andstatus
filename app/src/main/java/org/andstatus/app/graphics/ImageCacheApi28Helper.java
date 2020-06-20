/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Size;

import org.andstatus.app.data.MediaFile;
import org.andstatus.app.util.MyLog;

/** @author yvolk@yurivolkov.com
 * Methods extracted in a separate class to avoid initialization attempts of absent classes  */
@TargetApi(28)
public class ImageCacheApi28Helper {

    static CachedImage animatedFileToCachedImage(ImageCache imageCache, MediaFile mediaFile) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(mediaFile.downloadFile.getFile());
            Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, source1) -> {
                // To allow drawing bitmaps on Software canvases
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                setTargetSize(imageCache, mediaFile, decoder, info.getSize());
            });
            if (drawable instanceof BitmapDrawable) {
                return imageCache.bitmapToCachedImage(mediaFile, ((BitmapDrawable) drawable).getBitmap());
            }
            if (drawable instanceof Animatable) {
                ((Animatable) drawable).start();
            }
            return new CachedImage(mediaFile.downloadId, drawable);
        } catch (Exception e) {
            MyLog.i( imageCache.TAG, "Failed to decode " + mediaFile, e);
            return imageCache.imageFileToCachedImage(mediaFile);
        }
    }

    private static void setTargetSize(ImageCache imageCache, Object objTag, ImageDecoder decoder, Size imageSize) {
        int width = imageSize.getWidth();
        int height = imageSize.getHeight();
        while (height > imageCache.maxBitmapHeight || width > imageCache.maxBitmapWidth) {
            height = height * 3 / 4;
            width = width * 3 / 4;
        }
        if (width != imageSize.getWidth()) {
            MyLog.v(objTag, "Large bitmap " + imageSize + " scaled to " + width + "x" + height);
            decoder.setTargetSize(width, height);
        }
    }
}
