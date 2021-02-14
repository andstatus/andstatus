/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import org.andstatus.app.R
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.AttachedMediaFile
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.graphics.IdentifiableImageView
import org.andstatus.app.net.social.SpanUtil
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.SharedPreferencesUtil
import java.util.*
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
abstract class BaseNoteAdapter<T : BaseNoteViewItem<T?>?>(contextMenu: NoteContextMenu, listData: TimelineData<T?>?) : BaseTimelineAdapter<T?>(contextMenu.myContext, listData) {
    protected val showButtonsBelowNotes = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SHOW_BUTTONS_BELOW_NOTE, true)
    protected val contextMenu: NoteContextMenu?
    protected var preloadedImages: MutableSet<Long?>? = HashSet(100)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view = getEmptyView(convertView)
        view.setOnCreateContextMenuListener(contextMenu)
        view.setOnClickListener(this)
        setPosition(view, position)
        val item = getItem(position)
        populateView(view, item, false, position)
        return view
    }

    fun populateView(view: ViewGroup?, item: T?, showReceivedTime: Boolean, position: Int) {
        showRebloggers(view, item)
        MyUrlSpan.Companion.showText(view, R.id.note_author, item.author.actor.actorNameInTimelineWithOrigin, false, false)
        showNoteName(view, item)
        showNoteSummary(view, item)
        showNoteContent(view, item)
        MyUrlSpan.Companion.showText(view, R.id.note_details, item.getDetails(contextMenu.getActivity(), showReceivedTime)
                .toString(), false, false)
        showAvatarEtc(view, item)
        if (showAttachedImages) {
            showAttachedImages(view, item)
        }
        if (markRepliesToMe) {
            removeReplyToMeMarkerView(view)
            showMarkRepliesToMe(view, item)
        }
        if (showButtonsBelowNotes) {
            showButtonsBelowNote(view, item)
        } else {
            showFavorited(view, item)
        }
        showNoteNumberEtc(view, item, position)
    }

    protected abstract fun showAvatarEtc(view: ViewGroup?, item: T?)
    protected abstract fun showNoteNumberEtc(view: ViewGroup?, item: T?, position: Int)
    protected fun getEmptyView(convertView: View?): ViewGroup? {
        if (convertView == null) return newView()
        convertView.setBackgroundResource(0)
        val noteIndented = convertView.findViewById<View?>(R.id.note_indented)
        noteIndented.setBackgroundResource(0)
        return convertView as ViewGroup?
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).getNoteId()
    }

    protected fun newView(): ViewGroup? {
        val view = LayoutInflater.from(contextMenu.getActivity()).inflate(R.layout.note, null) as ViewGroup
        setupButtons(view)
        return view
    }

    protected fun showRebloggers(view: View?, item: T?) {
        val viewGroup = view.findViewById<View?>(R.id.reblogged)
        if (viewGroup == null) {
            return
        } else if (item.isReblogged()) {
            viewGroup.visibility = View.VISIBLE
            val rebloggers = MyStringBuilder()
            item.rebloggers.values.forEach(Consumer { text: String? -> rebloggers.withComma(text) })
            MyUrlSpan.Companion.showText(viewGroup, R.id.rebloggers, rebloggers.toString(), false, false)
        } else {
            viewGroup.visibility = View.GONE
        }
    }

    protected fun showNoteName(view: View?, item: T?) {
        MyUrlSpan.Companion.showSpannable(view.findViewById<TextView?>(R.id.note_name),
                if (item.isSensitive() && !MyPreferences.isShowSensitiveContent()) SpanUtil.EMPTY else item.getName(), false)
    }

    protected fun showNoteSummary(view: View?, item: T?) {
        MyUrlSpan.Companion.showSpannable(view.findViewById<TextView?>(R.id.note_summary), item.getSummary(), false)
    }

    protected fun showNoteContent(view: View?, item: T?) {
        MyUrlSpan.Companion.showSpannable(view.findViewById<TextView?>(R.id.note_body),
                if (item.isSensitive() && !MyPreferences.isShowSensitiveContent()) SpannableString.valueOf("(" + myContext.context().getText(R.string.sensitive) + ")") else item.getContent(),
                false)
    }

    protected fun showAvatar(view: View?, item: T?) {
        item.author.showAvatar(contextMenu.getActivity(), view.findViewById(R.id.avatar_image))
    }

    private fun showAttachedImages(view: View?, item: T?) {
        val attachmentsList = view.findViewById<LinearLayout?>(R.id.attachments_wrapper) ?: return
        if (!contextMenu.getActivity().isMyResumed || item.isSensitive() && !MyPreferences.isShowSensitiveContent() ||
                !item.attachedImageFiles.imageOrLinkMayBeShown()) {
            attachmentsList.visibility = View.GONE
            return
        }
        attachmentsList.removeAllViewsInLayout()
        for (mediaFile in item.attachedImageFiles.list) {
            if (!mediaFile.imageOrLinkMayBeShown()) continue
            val attachmentLayout = if (mediaFile.imageMayBeShown()) if (mediaFile.isTargetVideo) R.layout.attachment_video_preview else R.layout.attachment_image else R.layout.attachment_link
            val attachmentView = LayoutInflater.from(contextMenu.getActivity())
                    .inflate(attachmentLayout, attachmentsList, false)
            if (mediaFile.imageMayBeShown()) {
                val imageView: IdentifiableImageView = attachmentView.findViewById(R.id.attachment_image)
                preloadedImages.add(item.getNoteId())
                mediaFile.showImage(contextMenu.getActivity(), imageView)
                setOnImageClick(imageView, mediaFile)
            } else {
                MyUrlSpan.Companion.showText(attachmentView, R.id.attachment_link,
                        mediaFile.targetUri.toString(), true, false)
            }
            attachmentsList.addView(attachmentView)
        }
        attachmentsList.visibility = View.VISIBLE
    }

    private fun setOnImageClick(imageView: View?, mediaFile: AttachedMediaFile?) {
        imageView.setOnClickListener(View.OnClickListener { view: View? -> contextMenu.menuContainer.activity.startActivity(mediaFile.intentToView()) })
    }

    fun removeReplyToMeMarkerView(view: ViewGroup?) {
        val oldView = view.findViewById<View?>(R.id.reply_timeline_marker)
        if (oldView != null) {
            view.removeView(oldView)
        }
    }

    private fun showMarkRepliesToMe(view: ViewGroup?, item: T?) {
        if (myContext.users().isMe(item.inReplyToActor.actor) &&
                !myContext.users().isMe(item.author.actor)) {
            val referencedView = view.findViewById<View?>(R.id.note_indented)
            val replyToMeMarkerView: ImageView = ConversationIndentImageView(myContext.context(), referencedView, dpToPixes(6),
                    R.drawable.reply_timeline_marker_light, R.drawable.reply_timeline_marker)
            replyToMeMarkerView.id = R.id.reply_timeline_marker
            view.addView(replyToMeMarkerView, 1)
            val layoutParams = replyToMeMarkerView.layoutParams as RelativeLayout.LayoutParams
            layoutParams.leftMargin = dpToPixes(3)
        }
    }

    fun setupButtons(view: View?) {
        if (showButtonsBelowNotes) {
            val buttons = view.findViewById<View?>(R.id.note_buttons)
            if (buttons != null) {
                buttons.visibility = View.VISIBLE
                setOnButtonClick(buttons, R.id.reply_button, NoteContextMenuItem.REPLY)
                setOnButtonClick(buttons, R.id.reblog_button, NoteContextMenuItem.ANNOUNCE)
                setOnButtonClick(buttons, R.id.reblog_button_tinted, NoteContextMenuItem.UNDO_ANNOUNCE)
                setOnButtonClick(buttons, R.id.favorite_button, NoteContextMenuItem.LIKE)
                setOnButtonClick(buttons, R.id.favorite_button_tinted, NoteContextMenuItem.UNDO_LIKE)
                setOnClickShowContextMenu(buttons, R.id.more_button)
            }
        }
    }

    private fun setOnButtonClick(viewGroup: View?, buttonId: Int, menuItem: NoteContextMenuItem?) {
        viewGroup.findViewById<View?>(buttonId).setOnClickListener { view: View? ->
            val item = getItem(view)
            if (item != null && (item.noteStatus == DownloadStatus.LOADED || menuItem.appliedToUnsentNotesAlso)) {
                contextMenu.onCreateContextMenu(null, view, null, Consumer { contextMenu: NoteContextMenu? -> contextMenu.onContextItemSelected(menuItem, item.noteId) })
            }
        }
    }

    private fun setOnClickShowContextMenu(viewGroup: View?, buttonId: Int) {
        viewGroup.findViewById<View?>(buttonId).setOnClickListener { view: View? -> viewGroup.showContextMenu() }
    }

    protected fun showButtonsBelowNote(view: View?, item: T?) {
        val viewGroup = view.findViewById<View?>(R.id.note_buttons)
        if (viewGroup == null) {
            return
        } else if (showButtonsBelowNotes && item.noteStatus == DownloadStatus.LOADED) {
            viewGroup.visibility = View.VISIBLE
            tintIcon(viewGroup, item.reblogged, R.id.reblog_button, R.id.reblog_button_tinted)
            tintIcon(viewGroup, item.favorited, R.id.favorite_button, R.id.favorite_button_tinted)
            MyUrlSpan.Companion.showText(viewGroup, R.id.likes_count, I18n.notZero(item.likesCount), false, true)
            MyUrlSpan.Companion.showText(viewGroup, R.id.reblogs_count, I18n.notZero(item.reblogsCount), false, true)
            MyUrlSpan.Companion.showText(viewGroup, R.id.replies_count, I18n.notZero(item.repliesCount), false, true)
        } else {
            viewGroup.visibility = View.GONE
        }
    }

    private fun tintIcon(viewGroup: View?, colored: Boolean, viewId: Int, viewIdColored: Int) {
        val imageView = viewGroup.findViewById<ImageView?>(viewId)
        val imageViewTinted = viewGroup.findViewById<ImageView?>(viewIdColored)
        imageView.visibility = if (colored) View.GONE else View.VISIBLE
        imageViewTinted.visibility = if (colored) View.VISIBLE else View.GONE
    }

    protected fun showFavorited(view: View?, item: T?) {
        val favorited = view.findViewById<View?>(R.id.note_favorited)
        favorited.visibility = if (item.favorited) View.VISIBLE else View.GONE
    }

    init {
        this.contextMenu = contextMenu
    }
}