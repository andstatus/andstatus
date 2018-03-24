/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.graphics.Point;

public class MediaMetadata {
    public static final MediaMetadata EMPTY = new MediaMetadata(0, 0, 0);
    public final Point size;
    public final long duration;

    public MediaMetadata(int width, int height, long duration) {
        this.size = new Point(width, height);
        this.duration = duration;
    }
}
