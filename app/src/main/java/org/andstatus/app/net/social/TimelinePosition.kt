/*
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
package org.andstatus.app.net.social

import android.net.Uri
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils

import java.util.*

/**
 * Since introducing support for Pump.Io it appeared that
 * Position in the Timeline and Id of the Note may be different things.
 * @author yvolk@yurivolkov.com
 */
class TimelinePosition private constructor(position: String?) : IsEmpty {
    private val position: String = if (position.isNullOrEmpty()) "" else position

    fun getPosition(): String {
        return position
    }

    fun optUri(): Optional<Uri> {
        return UriUtils.toDownloadableOptional(position)
    }

    override fun toString(): String {
        return position
    }

    fun isTemp(): Boolean {
        return StringUtil.isTemp(position)
    }

    override val isEmpty: Boolean
        get() {
            return position.isEmpty()
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return if (other !is TimelinePosition) false else position == other.position
    }

    override fun hashCode(): Int {
        return position.hashCode()
    }

    companion object {
        val EMPTY: TimelinePosition = TimelinePosition("")

        fun of(position: String?): TimelinePosition =
                if (position.isNullOrEmpty()) EMPTY else TimelinePosition(position)
    }
}
