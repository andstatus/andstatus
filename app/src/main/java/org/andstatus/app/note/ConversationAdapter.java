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

package org.andstatus.app.note;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.TimelinePage;
import org.andstatus.app.timeline.TimelineParameters;
import org.andstatus.app.timeline.WhichPage;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.ViewUtils;

import java.util.Collections;
import java.util.List;

public class ConversationAdapter extends BaseNoteAdapter<ConversationViewItem> {
    private final MyActivity context;
    private final long selectedNoteId;
    private final boolean showThreads;

    ConversationAdapter(NoteContextMenu contextMenu,
                        Origin origin, long selectedNoteId,
                        List<ConversationViewItem> oMsgs,
                        boolean showThreads,
                        boolean oldNotesFirst) {
        super(contextMenu,
                new TimelineData<>(
                        null,
                        new TimelinePage<>(
                                new TimelineParameters(contextMenu.getMyContext(),
                                        Timeline.getTimeline(TimelineType.CONVERSATION, 0, origin), WhichPage.EMPTY),
                                oMsgs
                        )
                )
        );
        this.context = this.contextMenu.getActivity();
        this.selectedNoteId = selectedNoteId;
        this.showThreads = showThreads;
        for (ConversationItem oMsg : oMsgs) {
            oMsg.setReversedListOrder(oldNotesFirst);
            setInReplyToViewItem(oMsgs, oMsg);
        }
        Collections.sort(oMsgs);
    }

    private void setInReplyToViewItem(List<ConversationViewItem> oMsgs, ConversationItem viewItem) {
        if (viewItem.inReplyToNoteId == 0) {
            return;
        }
        for (ConversationViewItem oMsg : oMsgs) {
            if (oMsg.getNoteId() == viewItem.inReplyToNoteId) {
                viewItem.inReplyToViewItem = oMsg;
                break;
            }
        }
    }

    public void showAvatarEtc(ViewGroup view, ConversationViewItem item) {
        int indentPixels = getIndentPixels(item);
        showIndentImage(view, indentPixels);
        showDivider(view, indentPixels == 0 ? 0 : R.id.indent_image);
        if (showAvatars) {
            indentPixels = showAvatar(view, item, indentPixels);
        }
        indentedNote(view, indentPixels);
        showCentralItem(view, item);
    }

    public int getIndentPixels(ConversationViewItem item) {
        final int indentLevel = showThreads ? item.indentLevel : 0;
        return dpToPixes(10) * indentLevel;
    }

    private void showCentralItem(View view, ConversationViewItem item) {
        if (item.getNoteId() == selectedNoteId && getCount() > 1) {
            view.findViewById(R.id.note_indented).setBackground(
                    ImageCaches.getStyledImage(
                            R.drawable.current_message_background_light,
                            R.drawable.current_message_background).getDrawable());
        }
    }

    private void showIndentImage(View view, int indentPixels) {
        View referencedView = view.findViewById(R.id.note_indented);
        ViewGroup parentView = ((ViewGroup) referencedView.getParent());
        ImageView oldView = parentView.findViewById(R.id.indent_image);
        if (oldView != null) {
            parentView.removeView(oldView);
        }
        if (indentPixels > 0) {
            ImageView indentView = new ConversationIndentImageView(context, referencedView, indentPixels,
                    R.drawable.conversation_indent3, R.drawable.conversation_indent3);
            indentView.setId(R.id.indent_image);
            parentView.addView(indentView, 0);
        }
    }

    private void showDivider(View view, int viewToTheLeftId) {
        View divider = view.findViewById(R.id.divider);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        setRightOf(layoutParams, viewToTheLeftId);
        divider.setLayoutParams(layoutParams);
    }

    private int showAvatar(View view, BaseNoteViewItem item, int indentPixels) {
        AvatarView avatarView = view.findViewById(R.id.avatar_image);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) avatarView.getLayoutParams();
        layoutParams.leftMargin = dpToPixes(indentPixels == 0 ? 2 : 1) + indentPixels;
        avatarView.setLayoutParams(layoutParams);
        item.author.showAvatar(context, avatarView);
        return ViewUtils.getWidthWithMargins(avatarView);
    }

    private void setRightOf(RelativeLayout.LayoutParams layoutParams, int viewToTheLeftId) {
        if (viewToTheLeftId == 0) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
        }
    }

    private void indentedNote(View view, int indentPixels) {
        View msgView = view.findViewById(R.id.note_indented);
        msgView.setPadding(indentPixels + 6, msgView.getPaddingTop(), msgView.getPaddingRight(),
                msgView.getPaddingBottom());
    }

    @Override
    protected void showNoteNumberEtc(ViewGroup view, ConversationViewItem item, int position) {
        TextView number = view.findViewById(R.id.note_number);
        number.setText(Integer.toString(item.historyOrder));
    }
}
