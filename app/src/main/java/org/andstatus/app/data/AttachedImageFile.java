/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.graphics.AttachedImageView;
import org.andstatus.app.graphics.MyDrawableCache;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

public class AttachedImageFile {
    private static final String TAG = AttachedImageFile.class.getSimpleName();

    private final long downloadRowId;
    private final DownloadFile downloadFile;
    private Point size = null;
    public static final AttachedImageFile EMPTY = new AttachedImageFile(0, null);
    private static final Drawable BLANK_DRAWABLE = loadBlankDrawable();

    private static Drawable loadBlankDrawable() {
        Drawable drawable = null;
        MyLog.v(AvatarFile.class, "Loading blank image");
        Context context = MyContextHolder.get().context();
        if (context != null) {
            drawable = MyImageCache.getDrawableCompat(context, R.drawable.blank_image);
        }
        return drawable;
    }

    public static AttachedImageFile fromCursor(Cursor cursor) {
        return new AttachedImageFile(
                DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
    }

    public AttachedImageFile(long downloadRowIdIn, String filename) {
        downloadRowId = downloadRowIdIn;
        downloadFile = new DownloadFile(filename);
    }

    public Point getSize() {
        if (size == null && downloadFile.exists()) {
            size = MyImageCache.getAttachedImageSize(downloadFile.getFilePath());
        }
        return size == null ? new Point() : size;
    }

    public Drawable getDrawableFromCache() {
        return MyImageCache.getCachedAttachedImageDrawable(this, downloadFile.getFilePath());
    }

    public Drawable getDrawableSync() {
        if (downloadFile.exists()) {
            return MyImageCache.getAttachedImageDrawable(this, downloadFile.getFilePath());
        }
        if (downloadRowId == 0) {
            // TODO: Why we get here?
            MyLog.d(this, "rowId=0; " + downloadFile);
        } else {
            DownloadData.asyncRequestDownload(downloadRowId);
        }
        return null;
    }

    @Override
    public String toString() {
        return MyLog.objTagToString(this) + " [rowId=" + downloadRowId + ", " + downloadFile + "]";
    }

    public void preloadAttachedImage(@NonNull MyActivity myActivity) {
        Drawable drawable = getDrawableFromCache();
        if (drawable != null) {
            return;
        }
        if (downloadFile.exists()) {
            setImageDrawableAsync(myActivity, null, downloadFile.getFilePath(), "-preload");
        }
    }

    public void showAttachedImage(@NonNull MyActivity myActivity, ImageView imageView) {
        if (imageView == null || !myActivity.isResumedMy()) {
            return;
        }
        if (isEmpty()) {
            imageView.setVisibility(View.GONE);
            return;
        }
        if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
            ((AttachedImageView) imageView).setMeasuresLocked(false);
        }
        Drawable drawable = getDrawableFromCache();
        if (drawable == MyDrawableCache.BROKEN) {
            imageView.setVisibility(View.GONE);
            return;
        } else if (drawable != null) {
            imageView.setImageDrawable(drawable);
            imageView.setVisibility(View.VISIBLE);
            return;
        }
        if (downloadFile.exists()) {
            imageView.setImageDrawable(BLANK_DRAWABLE);
            imageView.setVisibility(View.VISIBLE);
            setImageDrawableAsync(myActivity, imageView, downloadFile.getFilePath(), "-load");
        } else {
            imageView.setVisibility(View.GONE);
            if (downloadRowId == 0) {
                // TODO: Why we get here?
                MyLog.d(this, "rowId=0; " + downloadFile);
            } else {
                DownloadData.asyncRequestDownload(downloadRowId);
            }
        }
    }

    private void setImageDrawableAsync(final MyActivity myActivity,
                                       @Nullable final ImageView imageView, final String path,
                                       final String taskSuffix) {
        AsyncTaskLauncher.execute(this, false,
                new MyAsyncTask<Void, Void, Drawable>(TAG + downloadRowId + taskSuffix,
                        MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Drawable doInBackground2(Void... params) {
                        return MyImageCache.getAttachedImageDrawable(this, path);
                    }

                    @Override
                    protected void onFinished(Drawable drawable, boolean success) {
                        if (imageView == null || !myActivity.isResumedMy()) {
                            return;
                        }
                        if (drawable == null) {
                            MyLog.v(this, "Failed to load attached image: " + path);
                        } else {
                            try {
                                if (AttachedImageView.class.isAssignableFrom(imageView.getClass())) {
                                    ((AttachedImageView) imageView).setMeasuresLocked(true);
                                }
                                imageView.setImageDrawable(drawable);
                                MyLog.v(this, "Attached image loaded: " + path);
                            } catch (Exception e) {
                                MyLog.d(this, "Error on setting image: " + path, e);
                            }

                        }
                    }
                });
    }

    public boolean isEmpty() {
        return downloadRowId==0 || downloadFile.isEmpty();
    }
}
