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
import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.msg.ActionableMessageList;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

public class AttachedImageFile {
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
                DbUtils.getLong(cursor, MyDatabase.Download.IMAGE_ID),
                DbUtils.getString(cursor, MyDatabase.Download.IMAGE_FILE_NAME));
    }

    public AttachedImageFile(long downloadRowIdIn, String filename) {
        downloadRowId = downloadRowIdIn;
        downloadFile = new DownloadFile(filename);
    }

    public Point getSize() {
        if (size == null && downloadFile.exists()) {
            size = MyImageCache.getImageSize(downloadFile.getFilePath());
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

    public static final double MAX_ATTACHED_IMAGE_PART = 0.75;

    @Override
    public String toString() {
        return MyLog.objTagToString(this) + " [rowId=" + downloadRowId + ", " + downloadFile + "]";
    }

    public void showAttachedImage(@NonNull ActionableMessageList messageList, ImageView imageView) {
        if (imageView == null || messageList.isPaused()) {
            return;
        }
        if (isEmpty()) {
            imageView.setVisibility(View.GONE);
        } else if (downloadFile.exists()) {
            Drawable drawable = getDrawableFromCache();
            if (drawable != null) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(drawable);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageDrawable(BLANK_DRAWABLE);
                setImageDrawableAsync(messageList, imageView, downloadFile.getFilePath());
            }
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

    private void setImageDrawableAsync(final ActionableMessageList messageList,
                                       final ImageView imageView, final String path) {
        AsyncTaskLauncher.execute(this,
                new MyAsyncTask<Void, Void, Drawable>(MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected Drawable doInBackground2(Void... params) {
                        return MyImageCache.getAttachedImageDrawable(this, path);
                    }

                    @Override
                    protected void onCancelled(Drawable drawable) {
                        onEnded(drawable);
                    }

                    @Override
                    protected void onPostExecute(Drawable drawable) {
                        onEnded(drawable);
                        super.onPostExecute(drawable);
                    }

                    private void onEnded(Drawable drawable) {
                        if ( messageList.getActivity().isPaused()) {
                            return;
                        }
                        if (drawable == null) {
                            imageView.setVisibility(View.GONE);
                            MyLog.v(this, "Failed to load attached image: " + path);
                        } else {
                            try {
                                imageView.setVisibility(View.VISIBLE);
                                imageView.setImageDrawable(drawable);
                                MyLog.v(this, "Attached image loaded: " + path);
                            } catch (Exception e) {
                                MyLog.d(this, "Error on setting image: " + path, e);
                            }

                        }
                        super.onPostExecute(drawable);
                    }
                });
    }

    public boolean isEmpty() {
        return downloadRowId==0 || downloadFile.isEmpty();
    }
}
