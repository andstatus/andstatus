/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import org.andstatus.app.util.MyLog;

/**
 * This custom ImageView allows dynamically crop its image according to the height of the other view
 * @author yvolk@yurivolkov.com
 */
public class ConversationIndentImageView extends ImageView {
    private View referencedView;
    private int widthPixels;
    private final static int MIN_HEIGH = 80;
    /** It's a height of the underlying bitmap (not cropped) */
    private final static int MAX_HEIGH = 1000;
    
    public ConversationIndentImageView(Context contextIn, View referencedViewIn, int widthPixelsIn) {
        super(contextIn);
        referencedView = referencedViewIn;
        widthPixels = widthPixelsIn;
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(widthPixels, MIN_HEIGH);
        setScaleType(ScaleType.MATRIX);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        setLayoutParams(layoutParams);
        setImageResource(R.drawable.conversation_indent3);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = referencedView.getMeasuredHeight();
        int mode = MeasureSpec.EXACTLY;
        MyLog.v(this, "onMeasure, indent=" + widthPixels + ", height=" + height);
        if (height == 0) {
            height = MAX_HEIGH;
            mode = MeasureSpec.AT_MOST;
        } else {
            getLayoutParams().height = height;
        }
        int measuredWidth = MeasureSpec.makeMeasureSpec(widthPixels,  MeasureSpec.EXACTLY);
        int measuredHeight = MeasureSpec.makeMeasureSpec(height, mode);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }
}
