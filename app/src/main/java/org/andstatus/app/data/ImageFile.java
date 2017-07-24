/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.graphics.AttachedImageView;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.IdentifiableImageView;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

public abstract class ImageFile {
    static final CachedImage BLANK_IMAGE = loadBlankImage();
    private final DownloadFile downloadFile;
    private volatile Point size = null;

    ImageFile(String filename) {
        downloadFile = new DownloadFile(filename);
    }

    public void showImage(@NonNull MyActivity myActivity, IdentifiableImageView imageView) {
        if (imageView == null || !myActivity.isResumedMy()) {
            return;
        }
        if (isEmpty()) {
            onNoImage(imageView);
            return;
        }
        if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
            ((AttachedImageView) imageView).setMeasuresLocked(false);
        }
        CachedImage image = getImageFromCache();
        if (image == CachedImage.BROKEN) {
            onNoImage(imageView);
            return;
        } else if (image != null && !image.isExpired()) {
            imageView.setImageDrawable(image.getDrawable());
            imageView.setVisibility(View.VISIBLE);
            return;
        }
        if (downloadFile.exists()) {
            showBlankImage(imageView);
        } else {
            onNoImage(imageView);
        }
        if (downloadFile.exists()) {
            showImageAsync(myActivity, imageView, false);
        } else {
            requestAsyncDownload();
        }
    }

    private void showBlankImage(ImageView imageView) {
        imageView.setImageDrawable(BLANK_IMAGE.getDrawable());
        imageView.setVisibility(View.VISIBLE);
    }

    private void showDefaultImage(ImageView imageView) {
        imageView.setImageDrawable(getDefaultImage().getDrawable());
        imageView.setVisibility(View.VISIBLE);
    }

    private void onNoImage(ImageView imageView) {
        if (isDefaultImageRequired()) {
            showDefaultImage(imageView);
        } else {
            imageView.setVisibility(View.GONE);
        }
    }

    private CachedImage getImageFromCache() {
        return ImageCaches.getCachedImage(getCacheName(), this, getId(), downloadFile.getFilePath());
    }

    public void preloadImageAsync(@NonNull MyActivity myActivity) {
        CachedImage image = getImageFromCache();
        if (image != null) {
            return;
        }
        if (downloadFile.exists()) {
            showImageAsync(myActivity, null, true);
        }
    }

    public CachedImage loadAndGetImage() {
        if (downloadFile.exists()) {
            return ImageCaches.loadAndGetImage(getCacheName(), this, getId(), downloadFile.getFilePath());
        }
        requestAsyncDownload();
        return null;
    }

    private void showImageAsync(final MyActivity myActivity, @Nullable final ImageView imageView,
                                final boolean preload) {
        final String path = downloadFile.getFilePath();
        String taskId = MyLog.objToTag(this) + getId() + (preload ? "-preload" : "-load");
        AsyncTaskLauncher.execute(this, false,
                new MyAsyncTask<Void, Void, CachedImage>(taskId, MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected CachedImage doInBackground2(Void... params) {
                        return ImageCaches.loadAndGetImage(getCacheName(), this, getId(), path);
                    }

                    @Override
                    protected void onFinish(CachedImage image, boolean success) {
                        final String method = "showImageAsync";
                        if (preload) {
                            if (image == null) {
                                MyLog.v(this, method + "; Failed to preload " + getCacheName() + ": " + path);
                            } else {
                                MyLog.v(this, method + "; Preloaded " + getCacheName() + ": " + path);
                            }
                            return;
                        }
                        if (imageView == null) {
                            MyLog.d(this, method + "; Skipped no view " + getCacheName() + ": " + path);
                            return;
                        }
                        if (!myActivity.isResumedMy()) {
                            MyLog.v(this, method + "; Skipped not resumed " + getCacheName() + ": " + path);
                            return;
                        }
                        if (image == null) {
                            MyLog.v(this, method + "; Failed to load " + getCacheName() + ": " + path);
                        } else {
                            try {
                                if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
                                    ((AttachedImageView) imageView).setMeasuresLocked(true);
                                }
                                imageView.setImageDrawable(image.getDrawable());
                                MyLog.v(this, method + "; Loaded" + getCacheName() + ": " + path);
                            } catch (Exception e) {
                                MyLog.d(this, method + "; Error on setting image " + getCacheName() + ": " + path, e);
                            }

                        }
                    }
                });
    }

    public Point getSize() {
        if (size == null && downloadFile.exists()) {
            size = ImageCaches.getImageSize(getCacheName(), getId(), downloadFile.getFilePath());
        }
        return size == null ? new Point() : size;
    }

    public boolean isEmpty() {
        return getId()==0 || downloadFile.isEmpty();
    }

    @Override
    public String toString() {
        return MyLog.objToTag(this) + ":{id=" + getId() + ", " + downloadFile + "}";
    }

    private static CachedImage loadBlankImage() {
        CachedImage image = null;
        MyLog.v(AvatarFile.class, "Loading blank image");
        Context context = MyContextHolder.get().context();
        if (context != null) {
            image = ImageCaches.getImageCompat(context, R.drawable.blank_image);
        }
        return image;
    }

    public abstract CacheName getCacheName();

    protected abstract long getId();

    protected abstract CachedImage getDefaultImage();

    protected abstract void requestAsyncDownload();

    protected boolean isDefaultImageRequired() {
        return false;
    }
}
