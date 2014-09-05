package org.andstatus.app.support.android.v11.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;

public class MyImageView extends ImageView {
    public MyImageView(Context context) {
        super(context);
    }

    public MyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    
    /**
     * This very useful function is since API 11: {@link #resolveSizeAndState(int, int, int)}
     * so we copy-pasted it here for compatibility with API 8 
     */
    public static int myResolveSizeAndState(int size, int measureSpec, int childMeasuredState) {
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize =  MeasureSpec.getSize(measureSpec);
        int result = size;
        switch (specMode) {
        case MeasureSpec.AT_MOST:
            if (specSize < size) {
                result = specSize | MEASURED_STATE_TOO_SMALL;
            }
            break;
        case MeasureSpec.EXACTLY:
            result = size;
            break;
        case MeasureSpec.UNSPECIFIED:
        default:
            break;
        }
        return result | (childMeasuredState&MEASURED_STATE_MASK);
    }

    /**
     * See http://stackoverflow.com/questions/1016896/how-to-get-screen-dimensions
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public int getDisplayHeight() {
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        if (android.os.Build.VERSION.SDK_INT >= 13) {
            Point size = new Point();
            display.getSize(size);
            return size.y;        
        } else {
            return display.getHeight();
        }
    }
}
