/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Swipe detector from <a href="http://stackoverflow.com/questions/937313/android-basic-gesture-detection">Android - basic gesture detection</a>
 * (variant by Exterminator13 ) 
 */
public class ActivitySwipeDetector implements View.OnTouchListener {

    static final String logTag = "ActivitySwipeDetector";
    private SwipeInterface activity;
    private float downX, downY;
    private long timeDown;
    private final float MIN_DISTANCE;
    private final int VELOCITY;
    private final float MAX_OFF_PATH;

    public ActivitySwipeDetector(Context context, SwipeInterface activity){
        this.activity = activity;
        final ViewConfiguration vc = ViewConfiguration.get(context);
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        MIN_DISTANCE = vc.getScaledPagingTouchSlop() * dm.density;
        VELOCITY = vc.getScaledMinimumFlingVelocity();
        MAX_OFF_PATH = MIN_DISTANCE * 2;            
    }

    public void onRightToLeftSwipe(View v){
        MyLog.i(logTag, "RightToLeftSwipe!");
        activity.onRightToLeft(v);
    }

    public void onLeftToRightSwipe(View v){
        MyLog.i(logTag, "LeftToRightSwipe!");
        activity.onLeftToRight(v);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch(event.getAction()){
        case MotionEvent.ACTION_DOWN: {
            MyLog.d("onTouch", "ACTION_DOWN");
            timeDown = System.currentTimeMillis();
            downX = event.getX();
            downY = event.getY();
            return false;
        }
        case MotionEvent.ACTION_UP: {
            MyLog.d("onTouch", "ACTION_UP");
            long timeUp = System.currentTimeMillis();
            float upX = event.getX();
            float upY = event.getY();

            float deltaX = downX - upX;
            float absDeltaX = Math.abs(deltaX); 
            float deltaY = downY - upY;
            float absDeltaY = Math.abs(deltaY);

            long time = timeUp - timeDown;

            if (absDeltaY > MAX_OFF_PATH) {
                MyLog.v(logTag, String.format("absDeltaY=%.2f, MAX_OFF_PATH=%.2f", absDeltaY, MAX_OFF_PATH));
                return v.performClick();
            }

            final long M_SEC = 1000;
            if (absDeltaX > MIN_DISTANCE && absDeltaX > time * VELOCITY / M_SEC) {
                if(deltaX < 0) { this.onLeftToRightSwipe(v); return true; }
                if(deltaX > 0) { this.onRightToLeftSwipe(v); return true; }
            } else {
                MyLog.v(logTag, String.format("absDeltaX=%.2f, MIN_DISTANCE=%.2f, absDeltaX > MIN_DISTANCE=%b", absDeltaX, MIN_DISTANCE, (absDeltaX > MIN_DISTANCE)));
                MyLog.v(logTag, String.format("absDeltaX=%.2f, time=%d, VELOCITY=%d, time*VELOCITY/M_SEC=%d, absDeltaX > time * VELOCITY / M_SEC=%b", absDeltaX, time, VELOCITY, time * VELOCITY / M_SEC, (absDeltaX > time * VELOCITY / M_SEC)));
            }

        }
        }
        return false;
    }

}