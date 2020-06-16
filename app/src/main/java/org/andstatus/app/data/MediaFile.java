/*
 * Copyright (C) 2017-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.andstatus.app.MyActivity;
import org.andstatus.app.graphics.AttachedImageView;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.IdentifiableImageView;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TryUtils;

import java.util.function.Consumer;

import io.vavr.control.CheckedFunction;
import io.vavr.control.Try;

public abstract class MediaFile implements IsEmpty, IdentifiableInstance {
    public final DownloadFile downloadFile;
    volatile MediaMetadata mediaMetadata;
    public volatile MyContentType contentType;
    public final long downloadId;
    public final DownloadStatus downloadStatus;
    public final long downloadedDate;

    MediaFile(String filename, MyContentType contentType, MediaMetadata mediaMetadata, long downloadId,
              DownloadStatus downloadStatus,
              long downloadedDate) {
        downloadFile = new DownloadFile(filename);
        this.contentType = contentType;
        setMediaMetadata(mediaMetadata);
        this.downloadId = downloadId;
        this.downloadStatus = downloadStatus;
        this.downloadedDate = downloadedDate;
    }

    public void setMediaMetadata(MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
    }

    public boolean isVideo() {
        return contentType == MyContentType.VIDEO;
    }

    public void showImage(@NonNull MyActivity myActivity, IdentifiableImageView imageView) {
        if (imageView == null) return;
        imageView.setImageId(getId());
        if (!imageMayBeShown()) {
            onNoImage(imageView);
            return;
        }
        if (downloadStatus != DownloadStatus.LOADED) {
            showDefaultImage(imageView);
            requestDownload();
            return;
        }
        if (imageView instanceof AttachedImageView) {
            ((AttachedImageView) imageView).setMeasuresLocked(false);
        }
        final String taskSuffix = "-sync-" + imageView.myViewId;
        if (downloadFile.existed) {
            CachedImage cachedImage = getImageFromCache(imageView.getCacheName());
            if (cachedImage == CachedImage.BROKEN) {
                logResult("Broken", taskSuffix);
                onNoImage(imageView);
                return;
            } else if (cachedImage != null && !cachedImage.isExpired()) {
                logResult("Set", taskSuffix);
                imageView.setLoaded();
                imageView.setImageDrawable(cachedImage.getDrawable());
                imageView.setVisibility(View.VISIBLE);
                return;
            }
            logResult("Show blank", taskSuffix);
            showBlankImage(imageView);
            AsyncTaskLauncher.execute(new ImageLoader(this, myActivity, imageView),
                    ImageLoader::load, loader -> loader::set);
        } else {
            logResult("No image file", taskSuffix);
            onNoImage(imageView);
            requestDownload();
        }
    }

    public boolean imageMayBeShown() {
        return !isEmpty() && downloadStatus != DownloadStatus.HARD_ERROR &&
                (downloadStatus != DownloadStatus.LOADED || mediaMetadata.nonEmpty());
    }

    private void showBlankImage(ImageView imageView) {
        imageView.setImageDrawable(null);
        imageView.setVisibility(View.VISIBLE);
    }

    private void showDefaultImage(IdentifiableImageView imageView) {
        imageView.setLoaded();
        imageView.setImageDrawable(getDefaultImage().getDrawable());
        imageView.setVisibility(View.VISIBLE);
    }

    private void onNoImage(IdentifiableImageView imageView) {
        if (isDefaultImageRequired()) {
            showDefaultImage(imageView);
        } else {
            imageView.setVisibility(View.GONE);
        }
    }

    private CachedImage getImageFromCache(CacheName cacheName) {
        return ImageCaches.getCachedImage(cacheName, this);
    }

    public void preloadImageAsync(CacheName cacheName) {
        CachedImage image = getImageFromCache(cacheName);
        if (image == null && downloadFile.existed) {
            AsyncTaskLauncher.execute(() -> preloadImage(this, cacheName));
        }
    }

    public CachedImage loadAndGetImage(CacheName cacheName) {
        if (downloadFile.existed) {
            return ImageCaches.loadAndGetImage(cacheName, this);
        }
        requestDownload();
        return null;
    }

    private static class ImageLoader extends AbstractImageLoader {
        private final MyActivity myActivity;
        private final IdentifiableImageView imageView;
        private volatile boolean logged = false;

        private ImageLoader(MediaFile mediaFile, MyActivity myActivity, IdentifiableImageView imageView) {
            super(mediaFile, "-asyn-" + imageView.myViewId);
            this.myActivity = myActivity;
            this.imageView = imageView;
        }

        Try<CachedImage> load() {
            return TryUtils.ofNullable(
                skip()
                    ? null
                    : ImageCaches.loadAndGetImage(imageView.getCacheName(), mediaFile));
        }

        void set(Try<CachedImage> tryImage) {
            if (skip()) return;

            tryImage.onSuccess(image -> {
                if (image.id != mediaFile.getId()) {
                    logResult("Loaded wrong image.id:" + image.id);
                    return;
                }
                try {
                    if (imageView instanceof AttachedImageView) {
                        ((AttachedImageView) imageView).setMeasuresLocked(true);
                    }
                    imageView.setImageDrawable(image.getDrawable());
                    imageView.setLoaded();
                    logResult("Loaded");
                } catch (Exception e) {
                    MyLog.d(mediaFile, mediaFile.getMsgLog("Error on setting image", taskSuffix), e);
                }
            }).onFailure( e -> logResult("No success onFinish"));
        }

        private boolean skip() {
            if (!myActivity.isMyResumed()) {
                logResult("Skipped not resumed activity");
                return true;
            }
            if (imageView.isLoaded()) {
                logResult("Skipped already loaded");
                return true;
            }
            if (imageView.getImageId() != mediaFile.getId()) {
                logResult("Skipped view.imageId:" + imageView.getImageId());
                return true;
            }
            return false;
        }

        private void logResult(String msgLog) {
            if (!logged) {
                logged = true;
                mediaFile.logResult(msgLog, taskSuffix);
            }
        }
    }

    @NonNull
    private String getMsgLog(String msgLog, String taskSuffix) {
        return getTaskId(taskSuffix) + "; " + msgLog + " " + downloadFile.getFilePath();
    }

    static void preloadImage(MediaFile mediaFile, CacheName cacheName) {
        String taskSuffix = "-prel";
        CachedImage image = ImageCaches.loadAndGetImage(cacheName, mediaFile);
        if (image == null) {
            mediaFile.logResult("Failed to preload", taskSuffix);
        } else if (image.id != mediaFile.getId()) {
            mediaFile.logResult("Loaded wrong image.id:" + image.id, taskSuffix);
        } else {
            mediaFile.logResult("Preloaded", taskSuffix);
        }
    }

    public void loadDrawable(CheckedFunction<Drawable, Drawable> mapper, Consumer<Drawable> uiConsumer) {
        final String taskSuffix = "-syncd-" + uiConsumer.hashCode();
        if (downloadStatus != DownloadStatus.LOADED || !downloadFile.existed) {
            logResult("No image file", taskSuffix);
            requestDownload();
            uiConsumer.accept(null);
            return;
        }
        CacheName cacheName = this instanceof AvatarFile ? CacheName.AVATAR : CacheName.ATTACHED_IMAGE;
        CachedImage cachedImage = getImageFromCache(cacheName);
        if (cachedImage == CachedImage.BROKEN) {
            logResult("Broken", taskSuffix);
            uiConsumer.accept(null);
            return;
        } else if (cachedImage != null && !cachedImage.isExpired()) {
            logResult("Set", taskSuffix);
            uiConsumer.accept(cachedImage.getDrawable());
            return;
        }
        logResult("Show default", taskSuffix);
        uiConsumer.accept(null);
        AsyncTaskLauncher.execute(new DrawableLoader(this, cacheName),
                loader -> loader.load().map(mapper), loader -> drawableTry -> drawableTry.onSuccess(uiConsumer));
    }

    private static class DrawableLoader extends AbstractImageLoader {
        private final CacheName cacheName;

        private DrawableLoader(MediaFile mediaFile, CacheName cacheName) {
            super(mediaFile, "-asynd");
            this.cacheName = cacheName;
        }

        Try<Drawable> load() {
            return TryUtils.ofNullable(ImageCaches.loadAndGetImage(cacheName, mediaFile))
                    .map(CachedImage::getDrawable);
        }
    }

    private static class AbstractImageLoader implements IdentifiableInstance {
        final MediaFile mediaFile;
        final String taskSuffix;

        private AbstractImageLoader(MediaFile mediaFile, String taskSuffix) {
            this.mediaFile = mediaFile;
            this.taskSuffix = taskSuffix;
        }

        @Override
        public long getInstanceId() {
            return mediaFile.getId();
        }

        @Override
        public String instanceIdString() {
            return IdentifiableInstance.super.instanceIdString() + taskSuffix;
        }
    }

    private void logResult(String msgLog, String taskSuffix) {
        MyLog.v(this, () -> getMsgLog(msgLog, taskSuffix));
    }

    @NonNull
    private String getTaskId(String taskSuffix) {
        return instanceTag() + "-load" + taskSuffix;
    }

    public Point getSize() {
        if (mediaMetadata.isEmpty() && downloadFile.existed) {
            setMediaMetadata(MediaMetadata.fromFilePath(downloadFile.getFilePath()));
        }
        return mediaMetadata.size();
    }

    public boolean isEmpty() {
        return getId() == 0;
    }

    @Override
    public String toString() {
        return isEmpty() ? "EMPTY" : instanceTag() + ":" + downloadFile;
    }

    @Override
    public long getInstanceId() {
        return getId();
    }

    public long getId() {
        return downloadId;
    }

    protected abstract CachedImage getDefaultImage();

    protected abstract void requestDownload();

    protected boolean isDefaultImageRequired() {
        return false;
    }

    public String getPath() {
        return downloadFile.getFilePath();
    }
}
