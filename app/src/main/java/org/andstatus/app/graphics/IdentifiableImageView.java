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

package org.andstatus.app.graphics;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import org.andstatus.app.util.InstanceId;

/**
 * @author yvolk@yurivolkov.com
 */
public class IdentifiableImageView extends AppCompatImageView {
    public final long myViewId = InstanceId.next();
    private volatile long imageId = 0;
    private volatile boolean loaded = false;

    public IdentifiableImageView(Context context) {
        super(context);
    }

    public IdentifiableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IdentifiableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public long getImageId() {
        return imageId;
    }

    public void setImageId(long imageId) {
        this.imageId = imageId;
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public void setLoaded() {
        this.loaded = true;
    }
}
