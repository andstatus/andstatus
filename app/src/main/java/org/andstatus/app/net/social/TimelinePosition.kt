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
    private val position: String? = null
    fun getPosition(): String? {
        return position
    }

    fun optUri(): Optional<Uri?>? {
        return UriUtils.toDownloadableOptional(position)
    }

    override fun toString(): String {
        return position
    }

    fun isTemp(): Boolean {
        return StringUtil.isTemp(position)
    }

    override fun isEmpty(): Boolean {
        return position.isNullOrEmpty()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        return if (o !is TimelinePosition) false else position == (o as TimelinePosition?).position
    }

    override fun hashCode(): Int {
        return position.hashCode()
    }

    companion object {
        val EMPTY: TimelinePosition? = TimelinePosition("")
        fun of(position: String?): TimelinePosition? {
            return if (position.isNullOrEmpty()) {
                TimelinePosition.Companion.EMPTY
            } else {
                TimelinePosition(position)
            }
        }
    }

    init {
        if (position.isNullOrEmpty()) {
            this.position = ""
        } else {
            this.position = position
        }
    }
}