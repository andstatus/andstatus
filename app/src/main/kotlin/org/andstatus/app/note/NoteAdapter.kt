/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.view.View
import android.view.ViewGroup
import org.andstatus.app.R
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.util.MyUrlSpan

/**
 * @author yvolk@yurivolkov.com
 */
class NoteAdapter(contextMenu: NoteContextMenu, listData: TimelineData<NoteViewItem>) :
        BaseNoteAdapter<NoteViewItem>(contextMenu, listData) {
    private var positionPrev = -1
    private var itemNumberShownCounter = 0
    private val TOP_TEXT: String?

    override fun showAvatarEtc(view: ViewGroup, item: NoteViewItem) {
        if (showAvatars) {
            showAvatar(view, item)
        } else {
            val noteView = view.findViewById<View?>(R.id.note_indented)
            noteView?.setPadding(dpToPixes(2), 0, dpToPixes(6), dpToPixes(2))
        }
    }

    private fun preloadAttachments(position: Int) {
        if (positionPrev < 0 || position == positionPrev) {
            return
        }
        var positionToPreload = position
        for (i in 0..4) {
            positionToPreload = positionToPreload + if (position > positionPrev) 1 else -1
            if (positionToPreload < 0 || positionToPreload >= count) {
                break
            }
            val item = getItem(positionToPreload)
            if (!preloadedImages.contains(item.getNoteId())) {
                preloadedImages.add(item.getNoteId())
                item.attachedImageFiles.preloadImagesAsync()
                break
            }
        }
    }

    override fun showNoteNumberEtc(view: ViewGroup, item: NoteViewItem, position: Int) {
        preloadAttachments(position)
        val text: String? = when (position) {
            0 -> if (mayHaveYoungerPage()) "1" else TOP_TEXT
            1, 2 -> Integer.toString(position + 1)
            else -> if (itemNumberShownCounter < 3) Integer.toString(position + 1) else ""
        }
        MyUrlSpan.showText(view, R.id.note_number, text, linkify = false, showIfEmpty = false)
        itemNumberShownCounter++
        positionPrev = position
    }

    override fun onClick(v: View) {
        var handled = false
        if (MyPreferences.isLongPressToOpenContextMenu()) {
            val item = getItem(v)
            if (TimelineActivity::class.java.isAssignableFrom(contextMenu.getActivity().javaClass)) {
                (contextMenu.getActivity() as TimelineActivity<*>).onItemClick(item)
                handled = true
            }
        }
        if (!handled) {
            super.onClick(v)
        }
    }

    init {
        TOP_TEXT = myContext.context().getText(R.string.top).toString()
    }
}
