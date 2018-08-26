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

import android.graphics.Point;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.graphics.AttachedImageView;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.IdentifiableImageView;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;

public abstract class ImageFile implements IsEmpty {
    private final DownloadFile downloadFile;
    private volatile MediaMetadata mediaMetadata;
    public final long downloadId;
    public final DownloadStatus downloadStatus;
    public final long downloadedDate;

    ImageFile(String filename, MediaMetadata mediaMetadata, long downloadId, DownloadStatus downloadStatus,
              long downloadedDate) {
        downloadFile = new DownloadFile(filename);
        this.mediaMetadata = mediaMetadata;
        this.downloadId = downloadId;
        this.downloadStatus = downloadStatus;
        this.downloadedDate = downloadedDate;
    }

    public boolean isVideo() {
        return mediaMetadata.duration > 0;
    }

    public void showImage(@NonNull MyActivity myActivity, IdentifiableImageView imageView) {
        if (imageView == null) return;
        imageView.setImageId(getId());
        if (cannotBeShown()) {
            onNoImage(imageView);
            return;
        }
        if (downloadStatus != DownloadStatus.LOADED) {
            showDefaultImage(imageView);
            requestDownload();
            return;
        }
        if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
            ((AttachedImageView) imageView).setMeasuresLocked(false);
        }
        final String taskSuffix = "-sync-" + imageView.myViewId;
        if (downloadFile.existed) {
            CachedImage cachedImage = getImageFromCache();
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
            showImageAsync(myActivity, imageView);
        } else {
            logResult("No image file", taskSuffix);
            onNoImage(imageView);
            requestDownload();
        }
    }

    public boolean mayBeShown() {
        return !cannotBeShown();
    }

    boolean cannotBeShown() {
        return isEmpty() || downloadStatus == DownloadStatus.HARD_ERROR
                || downloadStatus == DownloadStatus.LOADED && mediaMetadata.isEmpty();
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

    private CachedImage getImageFromCache() {
        return ImageCaches.getCachedImage(getCacheName(), this);
    }

    public void preloadImageAsync() {
        CachedImage image = getImageFromCache();
        if (image != null) {
            return;
        }
        if (downloadFile.existed) {
            preloadAsync();
        }
    }

    public CachedImage loadAndGetImage() {
        if (downloadFile.existed) {
            return ImageCaches.loadAndGetImage(getCacheName(), this);
        }
        requestDownload();
        return null;
    }

    private void showImageAsync(final MyActivity myActivity, @NonNull final IdentifiableImageView imageView) {
        final String taskSuffix = "-asyn-" + imageView.myViewId;
        AsyncTaskLauncher.execute(this, false,
                new MyAsyncTask<Void, Void, CachedImage>(getTaskId(taskSuffix), MyAsyncTask.PoolEnum.QUICK_UI) {
                    private boolean logged = false;

                    @Override
                    protected CachedImage doInBackground2(Void... params) {
                        if (skip()) {
                            return null;
                        }
                        return ImageCaches.loadAndGetImage(getCacheName(), ImageFile.this);
                    }

                    @Override
                    protected void onFinish(CachedImage image, boolean success) {
                        if (!success) {
                            logResult("No success onFinish");
                            return;
                        }
                        if (image == null) {
                            logResult("Failed to load");
                            return;
                        }
                        if (skip()) {
                            return;
                        }
                        if (image.id != getId()) {
                            logResult("Loaded wrong image.id:" + image.id);
                            return;
                        }
                        try {
                            if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
                                ((AttachedImageView) imageView).setMeasuresLocked(true);
                            }
                            imageView.setImageDrawable(image.getDrawable());
                            imageView.setLoaded();
                            logResult("Loaded");
                        } catch (Exception e) {
                            MyLog.d(ImageFile.this, getMsgLog("Error on setting image", taskSuffix), e);
                        }
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
                        if (imageView.getImageId() != getId()) {
                            logResult("Skipped view.imageId:" + imageView.getImageId());
                            return true;
                        }
                        return false;
                    }

                    private void logResult(String msgLog) {
                        if (!logged) {
                            logged = true;
                            MyLog.v(ImageFile.this, () -> getMsgLog(msgLog, taskSuffix));
                        }
                    }

                });
    }

    @NonNull
    private String getMsgLog(String msgLog, String taskSuffix) {
        return getTaskId(taskSuffix) + "; " + msgLog + " " + downloadFile.getFilePath();
    }


    private void preloadAsync() {
        final String taskSuffix = "-prel";
        AsyncTaskLauncher.execute(this, false,
                new MyAsyncTask<Void, Void, Void>(getTaskId(taskSuffix), MyAsyncTask.PoolEnum.QUICK_UI) {

                    @Override
                    protected Void doInBackground2(Void... params) {
                        CachedImage image = ImageCaches.loadAndGetImage(getCacheName(), ImageFile.this);
                        if (image == null) {
                            logResult("Failed to preload", taskSuffix);
                        } else if (image.id != getId()) {
                            logResult("Loaded wrong image.id:" + image.id, taskSuffix);
                        } else {
                            logResult("Preloaded", taskSuffix);
                        }
                        return null;
                    }
                });
    }

    private void logResult(String msgLog, String taskSuffix) {
        MyLog.v(ImageFile.this, () -> getMsgLog(msgLog, taskSuffix));
    }

    @NonNull
    private String getTaskId(String taskSuffix) {
        return MyLog.objToTag(this) + "-" + getId() + "-load" + taskSuffix;
    }

    public Point getSize() {
        if (mediaMetadata.isEmpty() && downloadFile.existed) {
            mediaMetadata = MediaMetadata.fromFilePath(downloadFile.getFilePath());
        }
        return mediaMetadata.size();
    }

    public boolean isEmpty() {
        return getId()==0;
    }

    @Override
    public String toString() {
        return MyLog.objToTag(this) + ":{id=" + getId() + ", " + downloadFile + "}";
    }

    public abstract CacheName getCacheName();

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
