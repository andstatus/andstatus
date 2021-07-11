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
package org.andstatus.app.data

import android.content.Context
import org.andstatus.app.R

enum class DownloadStatus(private val code: Long, private val titleResourceId: Int, canBeDownloaded: CanBeDownloaded?) {
    LOADED(2, 0, CanBeDownloaded.YES),
    SOFT_ERROR(4, 0, CanBeDownloaded.NO),
    HARD_ERROR(5, 0, CanBeDownloaded.NO),
    ABSENT(6, 0, CanBeDownloaded.NO),
    SENDING(7, R.string.download_status_unsent, CanBeDownloaded.YES),
    SENT(11, R.string.sent, CanBeDownloaded.YES),
    DRAFT(8, R.string.download_status_draft, CanBeDownloaded.NO),
    DELETED(9, 0, CanBeDownloaded.NO),
    NEEDS_UPDATE(10, 0, CanBeDownloaded.YES),
    UNKNOWN(0, 0, CanBeDownloaded.YES);

    val canBeDownloaded: Boolean

    private enum class CanBeDownloaded {
        YES, NO
    }

    fun save(): Long {
        return code
    }

    fun getTitle(context: Context?): CharSequence {
        return if (titleResourceId == 0 || context == null) {
            this.toString()
        } else {
            context.getText(titleResourceId)
        }
    }

    fun mayBeSent(): Boolean {
        return mayBeSent(this)
    }

    fun isUnsentDraft(): Boolean {
        return when (this) {
            SENDING, DRAFT -> true
            else -> false
        }
    }

    fun mayUpdateContent(): Boolean {
        return when (this) {
            SENDING, DRAFT, LOADED -> true
            else -> false
        }
    }

    fun isPresentAtServer(): Boolean {
        return when (this) {
            SENT, LOADED -> true
            else -> false
        }
    }

    companion object {
        fun load(codeIn: Long): DownloadStatus {
            for (value in values()) {
                if (value.code == codeIn) {
                    return value
                }
            }
            return UNKNOWN
        }

        private fun mayBeSent(status: DownloadStatus?): Boolean {
            return when (status) {
                SENDING, HARD_ERROR, SOFT_ERROR -> true
                else -> false
            }
        }
    }

    init {
        this.canBeDownloaded = canBeDownloaded == CanBeDownloaded.YES
    }
}
