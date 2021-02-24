/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.util

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object InstanceId {
    /**
     * IDs used for testing purposes to identify instances of reference types.
     */
    private val PREV_INSTANCE_ID: AtomicLong = AtomicLong(0)

    /**
     * @return Unique for this process integer, numbers are given in the order starting from 1
     */
    operator fun next(): Long {
        return PREV_INSTANCE_ID.incrementAndGet()
    }

    // TODO: To be replaced with View.generateViewId() for API >= 17
    private val sNextViewId: AtomicInteger = AtomicInteger(1)

    /**
     * http://stackoverflow.com/questions/1714297/android-view-setidint-id-programmatically-how-to-avoid-id-conflicts
     * Generate a value suitable for use in [android.view.View.setId].
     * This value will not collide with ID values generated at build time by aapt for R.id.
     *
     * @return a generated ID value
     */
    fun generateViewId(): Int {
        while (true) {
            val result = sNextViewId.get()
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            var newValue = result + 1
            if (newValue > 0x00FFFFFF) newValue = 1 // Roll over to 1, not 0.
            if (sNextViewId.compareAndSet(result, newValue)) {
                return result
            }
        }
    }
}