/*
 * Copyright (C) 2014-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.timeline.TimelinePage
import org.andstatus.app.timeline.TimelineParameters
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.ViewUtils
import java.util.*

class ConversationAdapter internal constructor(contextMenu: NoteContextMenu,
                                               origin: Origin, selectedNoteId: Long,
                                               oMsgs: MutableList<ConversationViewItem>,
                                               showThreads: Boolean,
                                               oldNotesFirst: Boolean) : BaseNoteAdapter<ConversationViewItem>(contextMenu,
        TimelineData(
                null,
                TimelinePage(
                        TimelineParameters(contextMenu.getMyContext(),
                                contextMenu.getActivity().myContext.timelines
                                        .get(TimelineType.CONVERSATION, Actor.EMPTY, origin), WhichPage.EMPTY),
                        oMsgs
                )
        )
) {
    private val context: MyActivity
    private val selectedNoteId: Long
    private val showThreads: Boolean

    private fun setInReplyToViewItem(oMsgs: MutableList<ConversationViewItem>, viewItem: ConversationViewItem) {
        if (viewItem.inReplyToNoteId == 0L) {
            return
        }
        for (oMsg in oMsgs) {
            if (oMsg.getNoteId() == viewItem.inReplyToNoteId) {
                viewItem.inReplyToViewItem = oMsg
                break
            }
        }
    }

    public override fun showAvatarEtc(view: ViewGroup, item: ConversationViewItem) {
        var indentPixels = getIndentPixels(item)
        showIndentImage(view, indentPixels)
        showDivider(view, if (indentPixels == 0) 0 else R.id.indent_image)
        if (showAvatars) {
            indentPixels = showAvatar(view, item, indentPixels)
        }
        indentedNote(view, indentPixels)
        showCentralItem(view, item)
    }

    fun getIndentPixels(item: ConversationViewItem): Int {
        val indentLevel = if (showThreads) item.indentLevel else 0
        return dpToPixes(10) * indentLevel
    }

    private fun showCentralItem(view: View, item: ConversationViewItem) {
        if (item.getNoteId() == selectedNoteId && count > 1) {
            view.findViewById<View?>(R.id.note_indented).background = ImageCaches.getStyledImage(
                    R.drawable.current_message_background_light,
                    R.drawable.current_message_background).getDrawable()
        }
    }

    private fun showIndentImage(view: View, indentPixels: Int) {
        val referencedView = view.findViewById<View?>(R.id.note_indented)
        val parentView = referencedView.parent as ViewGroup
        val oldView = parentView.findViewById<ImageView?>(R.id.indent_image)
        if (oldView != null) {
            parentView.removeView(oldView)
        }
        if (indentPixels > 0) {
            val indentView: ImageView = ConversationIndentImageView(context, referencedView, indentPixels,
                    R.drawable.conversation_indent3, R.drawable.conversation_indent3)
            indentView.id = R.id.indent_image
            parentView.addView(indentView, 0)
        }
    }

    private fun showDivider(view: View, viewToTheLeftId: Int) {
        val divider = view.findViewById<View?>(R.id.divider)
        val layoutParams = divider.layoutParams as RelativeLayout.LayoutParams
        setRightOf(layoutParams, viewToTheLeftId)
        divider.layoutParams = layoutParams
    }

    private fun showAvatar(view: View, item: BaseNoteViewItem<*>, indentPixels: Int): Int {
        val avatarView: AvatarView = view.findViewById(R.id.avatar_image)
        val layoutParams = avatarView.layoutParams as RelativeLayout.LayoutParams
        layoutParams.leftMargin = dpToPixes(if (indentPixels == 0) 2 else 1) + indentPixels
        avatarView.layoutParams = layoutParams
        item.author.showAvatar(context, avatarView)
        return ViewUtils.getWidthWithMargins(avatarView)
    }

    private fun setRightOf(layoutParams: RelativeLayout.LayoutParams, viewToTheLeftId: Int) {
        if (viewToTheLeftId == 0) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE)
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId)
        }
    }

    private fun indentedNote(view: View, indentPixels: Int) {
        val msgView = view.findViewById<View?>(R.id.note_indented)
        msgView.setPadding(indentPixels + 6, msgView.paddingTop, msgView.paddingRight,
                msgView.paddingBottom)
    }

    override fun showNoteNumberEtc(view: ViewGroup, item: ConversationViewItem, position: Int) {
        val number = view.findViewById<TextView?>(R.id.note_number)
        number.text = item.historyOrder.toString()
    }

    init {
        context = this.contextMenu.getActivity()
        this.selectedNoteId = selectedNoteId
        this.showThreads = showThreads
        for (oMsg in oMsgs) {
            oMsg.reversedListOrder = oldNotesFirst
            setInReplyToViewItem(oMsgs, oMsg)
        }
        Collections.sort(oMsgs)
    }
}
