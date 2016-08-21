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

package org.andstatus.app.graphics;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

/**
 * The ImageView auto resizes to the width of the referenced view  
 * @author yvolk@yurivolkov.com
 */
public class AttachedImageView extends ImageView {
    public static final double MAX_ATTACHED_IMAGE_PART = 0.75;

    private View referencedView = null;
    private static final int MAX_HEIGHT = 2500;

    private boolean heightLocked = false;
    private int widthMeasureSpecStored = 0;
    private int heightMeasureSpecStored = 0;

    public AttachedImageView(Context context) {
        super(context);
    }

    public AttachedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AttachedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setReferencedView(View referencedViewIn) {
        referencedView = referencedViewIn;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final String method = "onMeasure";
        if (heightLocked && heightMeasureSpecStored != 0) {
            saveMeasureSpec(widthMeasureSpecStored, heightMeasureSpecStored);
            setMeasuredDimension(widthMeasureSpecStored, heightMeasureSpecStored);
            return;
        }
        if (referencedView == null) {
            referencedView =  ((View)getParent()).findViewById(R.id.message_body);
        }
        if (referencedView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            saveMeasureSpec(getMeasuredWidthAndState(), getMeasuredHeightAndState());
            return;
        }
        int refWidthPixels = referencedView.getMeasuredWidth();
        int height = (int) Math.floor(refWidthPixels * getDrawableHeightToWidthRatio());
        logIt(method, refWidthPixels, widthMeasureSpec, height);
        int mode = MeasureSpec.EXACTLY;
        if (height == 0) {
            height = MAX_HEIGHT;
            mode = MeasureSpec.AT_MOST;
        }
        if (height > MAX_ATTACHED_IMAGE_PART * getDisplayHeight()) {
            height = (int) Math.floor(MAX_ATTACHED_IMAGE_PART
                    * getDisplayHeight());
        }
        getLayoutParams().height = height;
        int widthSpec = MeasureSpec.makeMeasureSpec(refWidthPixels,  MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(height, mode);
        saveMeasureSpec(widthSpec, heightSpec);
        setMeasuredDimension(widthSpec, heightSpec);
    }

    /**
     * We need to catch an error here in order to work in Android Editor preview
     */
    private void logIt(String method, Integer refWidthPixels, int widthMeasureSpec, float height) {
        try {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + ";"
                        + (heightLocked ? "locked" : "      ")
                        + " height=" + height
                        + ", widthSpec=" + MeasureSpec.toString(widthMeasureSpec)
                        + (refWidthPixels == null ? "" : " refWidth=" + refWidthPixels + ",")
                );
            }
        } catch (Exception e) {
            Log.i(AttachedImageView.class.getSimpleName(), method + "; MyLog class was not found", e);
        }
    }

    private void saveMeasureSpec(int widthMeasureSpec, int heightMeasureSpec) {
        String method = "onMeasure";
        logIt(method, null, widthMeasureSpec, heightMeasureSpec);
        if (!heightLocked) {
            widthMeasureSpecStored = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(widthMeasureSpec),  MeasureSpec.AT_MOST);
            heightMeasureSpecStored = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(heightMeasureSpec),  MeasureSpec.EXACTLY);
        }
    }

    public int getDisplayHeight() {
        return MyImageCache.getDisplaySize(getContext()).y;
    }

    private float getDrawableHeightToWidthRatio() {
        float ratio = 9f / 19f;
        if (getDrawable() != null) {
            int width = getDrawable().getIntrinsicWidth();
            int height = getDrawable().getIntrinsicHeight();
            if (width > 0 && height > 0) {
                ratio = 1f * height / width;
            }
        }
        return ratio;
    }

    public void setMeasuresLocked(boolean locked) {
        heightLocked = locked;
    }

}
