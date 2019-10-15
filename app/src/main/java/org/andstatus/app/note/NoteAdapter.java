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

package org.andstatus.app.note;

import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class NoteAdapter extends BaseNoteAdapter<NoteViewItem> {
    private int positionPrev = -1;
    private int itemNumberShownCounter = 0;
    private final String TOP_TEXT;

    public NoteAdapter(NoteContextMenu contextMenu, TimelineData<NoteViewItem> listData) {
        super(contextMenu, listData);
        TOP_TEXT = myContext.context().getText(R.string.top).toString();
    }

    @Override
    protected void showAvatarEtc(ViewGroup view, NoteViewItem item) {
        if (showAvatars) {
            showAvatar(view, item);
        } else {
            View noteView = view.findViewById(R.id.note_indented);
            if (noteView != null) {
                noteView.setPadding(dpToPixes(2), 0, dpToPixes(6), dpToPixes(2));
            }
        }
    }

    private void preloadAttachments(int position) {
        if (positionPrev < 0 || position == positionPrev) {
            return;
        }
        Integer positionToPreload = position;
        for (int i = 0; i < 5; i++) {
            positionToPreload = positionToPreload + (position > positionPrev ? 1 : -1);
            if (positionToPreload < 0 || positionToPreload >= getCount()) {
                break;
            }
            NoteViewItem item = getItem(positionToPreload);
            if (!preloadedImages.contains(item.getNoteId())) {
                preloadedImages.add(item.getNoteId());
                item.attachedImageFiles.preloadImagesAsync();
                break;
            }
        }
    }

    @Override
    protected void showNoteNumberEtc(ViewGroup view, NoteViewItem item, int position) {
        preloadAttachments(position);
        String text;
        switch (position) {
            case 0:
                text = mayHaveYoungerPage() ? "1" : TOP_TEXT;
                break;
            case 1:
            case 2:
                text = Integer.toString(position + 1);
                break;
            default:
                text = itemNumberShownCounter < 3 ? Integer.toString(position + 1) : "";
                break;
        }
        MyUrlSpan.showText(view, R.id.note_number, text, false, false);
        itemNumberShownCounter++;
        positionPrev = position;
    }

    @Override
    public void onClick(View v) {
        boolean handled = false;
        if (MyPreferences.isLongPressToOpenContextMenu()) {
            NoteViewItem item = getItem(v);
            if (TimelineActivity.class.isAssignableFrom(contextMenu.getActivity().getClass())) {
                ((TimelineActivity) contextMenu.getActivity()).onItemClick(item);
                handled = true;
            }
        }
        if (!handled) {
            super.onClick(v);
        }
    }
}
