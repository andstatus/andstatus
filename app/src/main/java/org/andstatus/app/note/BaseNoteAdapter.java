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

package org.andstatus.app.note;

import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.graphics.IdentifiableImageView;
import org.andstatus.app.net.social.SpanUtil;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class BaseNoteAdapter<T extends BaseNoteViewItem<T>> extends BaseTimelineAdapter<T> {
    protected final boolean showButtonsBelowNotes =
            SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SHOW_BUTTONS_BELOW_NOTE, true);
    protected final NoteContextMenu contextMenu;
    protected Set<Long> preloadedImages = new HashSet<>(100);

    public BaseNoteAdapter(@NonNull NoteContextMenu contextMenu, TimelineData<T> listData) {
        super(contextMenu.getMyContext(), listData);
        this.contextMenu = contextMenu;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewGroup view = getEmptyView(convertView);
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        T item = getItem(position);
        populateView(view, item, false, position);
        return view;
    }

    public void populateView(ViewGroup view, T item, boolean showReceivedTime, int position) {
        showRebloggers(view, item);
        MyUrlSpan.showText(view, R.id.note_author, item.author.getActor().getViewItemActorName(), false, false);
        showNoteName(view, item);
        showNoteSummary(view, item);
        showNoteContent(view, item);
        MyUrlSpan.showText(view, R.id.note_details, item.getDetails(contextMenu.getActivity(), showReceivedTime)
                .toString(), false, false);

        showAvatarEtc(view, item);

        if (showAttachedImages) {
            showAttachedImages(view, item);
        }
        if (markRepliesToMe) {
            removeReplyToMeMarkerView(view);
            showMarkRepliesToMe(view, item);
        }
        if (showButtonsBelowNotes) {
            showButtonsBelowNote(view, item);
        } else {
            showFavorited(view, item);
        }
        showNoteNumberEtc(view, item, position);
    }

    protected abstract void showAvatarEtc(ViewGroup view, T item);

    protected abstract void showNoteNumberEtc(ViewGroup view, T item, int position);

    protected ViewGroup getEmptyView(View convertView) {
        if (convertView == null) return newView();
        convertView.setBackgroundResource(0);
        View noteIndented = convertView.findViewById(R.id.note_indented);
        noteIndented.setBackgroundResource(0);
        return (ViewGroup) convertView;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getNoteId();
    }

    protected ViewGroup newView() {
        ViewGroup view = (ViewGroup) LayoutInflater.from(contextMenu.getActivity()).inflate(R.layout.note, null);
        setupButtons(view);
        return view;
    }

    protected void showRebloggers(View view, T item) {
        View viewGroup = view.findViewById(R.id.reblogged);
        if (viewGroup == null) {
            return;
        } else if (item.isReblogged()) {
            viewGroup.setVisibility(View.VISIBLE);
            MyStringBuilder rebloggers = new MyStringBuilder();
            item.rebloggers.values().forEach(rebloggers::withComma);
            MyUrlSpan.showText(viewGroup, R.id.rebloggers, rebloggers.toString(), false, false);
        } else {
            viewGroup.setVisibility(View.GONE);
        }
    }

    protected void showNoteName(View view, T item) {
        MyUrlSpan.showSpannable(view.findViewById(R.id.note_name),
            item.isSensitive() && !MyPreferences.isShowSensitiveContent() ? SpanUtil.EMPTY : item.getName(), false);
    }

    protected void showNoteSummary(View view, T item) {
        MyUrlSpan.showSpannable(view.findViewById(R.id.note_summary), item.getSummary(), false);
    }

    protected void showNoteContent(View view, T item) {
        MyUrlSpan.showSpannable(view.findViewById(R.id.note_body),
            item.isSensitive() && !MyPreferences.isShowSensitiveContent()
                    ? SpannableString.valueOf("(" + myContext.context().getText(R.string.sensitive) + ")")
                    : item.getContent(),
                false);
    }

    protected void showAvatar(View view, T item) {
        item.author.showAvatar(contextMenu.getActivity(), view.findViewById(R.id.avatar_image));
    }

    private void showAttachedImages(View view, T item) {
        final LinearLayout attachmentsList = view.findViewById(R.id.attachments_wrapper);
        if (attachmentsList == null) return;

        if (!contextMenu.getActivity().isMyResumed() ||
            item.isSensitive() && !MyPreferences.isShowSensitiveContent() ||
            !item.attachedImageFiles.imageOrLinkMayBeShown()) {

            attachmentsList.setVisibility(View.GONE);
            return;
        }

        attachmentsList.removeAllViewsInLayout();

        for (AttachedImageFile imageFile: item.attachedImageFiles.list) {
            if (!imageFile.imageOrLinkMayBeShown()) continue;

            int attachmentLayout = imageFile.imageMayBeShown()
                    ? (imageFile.isTargetVideo() ? R.layout.attachment_video_preview : R.layout.attachment_image)
                    : R.layout.attachment_link;
            final View attachmentView = LayoutInflater.from(contextMenu.getActivity())
                    .inflate(attachmentLayout, attachmentsList, false);
            if (imageFile.imageMayBeShown()) {
                IdentifiableImageView imageView = attachmentView.findViewById(R.id.attachment_image);
                preloadedImages.add(item.getNoteId());
                imageFile.showImage(contextMenu.getActivity(), imageView);
                setOnImageClick(imageView, imageFile);
            } else {
                MyUrlSpan.showText(attachmentView, R.id.attachment_link,
                        imageFile.getTargetUri().toString(), true, false);
            }
            attachmentsList.addView(attachmentView);
        }

        attachmentsList.setVisibility(View.VISIBLE);
    }

    private void setOnImageClick(View imageView, AttachedImageFile imageFile) {
        imageView.setOnClickListener(view -> {
            contextMenu.menuContainer.getActivity().startActivity(imageFile.intentToView());
        });
    }

    public void removeReplyToMeMarkerView(ViewGroup view) {
        View oldView = view.findViewById(R.id.reply_timeline_marker);
        if (oldView != null) {
            view.removeView(oldView);
        }
    }

    private void showMarkRepliesToMe(ViewGroup view, T item) {
        if (myContext.users().isMe(item.inReplyToActor.getActor()) &&
                !myContext.users().isMe(item.author.getActor())) {
            View referencedView = view.findViewById(R.id.note_indented);
            ImageView replyToMeMarkerView = new ConversationIndentImageView(myContext.context(), referencedView, dpToPixes(6),
                    R.drawable.reply_timeline_marker_light, R.drawable.reply_timeline_marker);
            replyToMeMarkerView.setId(R.id.reply_timeline_marker);
            view.addView(replyToMeMarkerView, 1);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)  replyToMeMarkerView.getLayoutParams();
            layoutParams.leftMargin = dpToPixes(3);
        }
    }

    public void setupButtons(View view) {
        if (showButtonsBelowNotes) {
            View buttons = view.findViewById(R.id.note_buttons);
            if (buttons != null) {
                buttons.setVisibility(View.VISIBLE);
                setOnButtonClick(buttons, R.id.reply_button, NoteContextMenuItem.REPLY);
                setOnButtonClick(buttons, R.id.reblog_button, NoteContextMenuItem.ANNOUNCE);
                setOnButtonClick(buttons, R.id.reblog_button_tinted, NoteContextMenuItem.UNDO_ANNOUNCE);
                setOnButtonClick(buttons, R.id.favorite_button, NoteContextMenuItem.LIKE);
                setOnButtonClick(buttons, R.id.favorite_button_tinted, NoteContextMenuItem.UNDO_LIKE);
                setOnClickShowContextMenu(buttons, R.id.more_button);
            }
        }
    }

    private void setOnButtonClick(View viewGroup, int buttonId, NoteContextMenuItem menuItem) {
        viewGroup.findViewById(buttonId).setOnClickListener(view -> {
            T item = getItem(view);
            if (item != null && (item.noteStatus == DownloadStatus.LOADED || menuItem.appliedToUnsentNotesAlso)) {
                contextMenu.onCreateContextMenu(null, view, null, contextMenu -> {
                    contextMenu.onContextItemSelected(menuItem, item.getNoteId());
                });
            }
        });
    }

    private void setOnClickShowContextMenu(final View viewGroup, int buttonId) {
        viewGroup.findViewById(buttonId).setOnClickListener(view -> {
            viewGroup.showContextMenu();
        });
    }

    protected void showButtonsBelowNote(View view, T item) {
        View viewGroup = view.findViewById(R.id.note_buttons);
        if (viewGroup == null) {
            return;
        } else if (showButtonsBelowNotes && item.noteStatus == DownloadStatus.LOADED) {
            viewGroup.setVisibility(View.VISIBLE);
            tintIcon(viewGroup, item.reblogged, R.id.reblog_button, R.id.reblog_button_tinted);
            tintIcon(viewGroup, item.favorited, R.id.favorite_button, R.id.favorite_button_tinted);
        } else {
            viewGroup.setVisibility(View.GONE);
        }
    }

    private void tintIcon(View viewGroup, boolean colored, int viewId, int viewIdColored) {
        ImageView imageView = viewGroup.findViewById(viewId);
        ImageView imageViewTinted = viewGroup.findViewById(viewIdColored);
        imageView.setVisibility(colored ? View.GONE : View.VISIBLE);
        imageViewTinted.setVisibility(colored ? View.VISIBLE : View.GONE);
    }

    protected void showFavorited(View view, T item) {
        View favorited = view.findViewById(R.id.note_favorited);
        favorited.setVisibility(item.favorited ? View.VISIBLE : View.GONE );
    }
}
