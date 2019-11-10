/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.note.NoteDownloads;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Attachments implements IsEmpty {
    public static final Attachments EMPTY = new Attachments(true);
    public final List<Attachment> list;

    public Attachments() {
        this(false);
    }

    private Attachments(boolean isEmpty) {
        list = isEmpty ? Collections.emptyList() : new ArrayList<>();
    }

    public Attachments add(Attachment attachment) {
        if (!attachment.isValid() || list.contains(attachment)) return this;
        Attachments attachments = new Attachments(false);
        attachments.list.addAll(list);
        attachments.list.add(attachment);
        return attachments;
    }

    public void save(CommandExecutionContext execContext, long noteId) {
        renumber();
        List<DownloadData> downloads = new ArrayList<>();
        for (Attachment attachment : list) {
            DownloadData dd = DownloadData.fromAttachment(noteId, attachment);
            dd.setDownloadNumber(attachment.downloadNumber);
            if (attachment.previewOf.nonEmpty()) {
                dd.setPreviewOfDownloadId(
                    downloads.stream().filter(d -> d.getUri().equals(attachment.previewOf.uri)).findAny()
                        .map(DownloadData::getDownloadId).orElse(0L)
                );
            }
            dd.saveToDatabase();
            downloads.add(dd);
            switch (dd.getStatus()) {
                case LOADED:
                case HARD_ERROR:
                    break;
                default:
                    if (UriUtils.isDownloadable(dd.getUri())) {
                        if (attachment.contentType.getDownloadMediaOfThisType()) {
                            dd.requestDownload(execContext.myContext);
                        }
                    } else {
                        AttachmentDownloader.load(dd, execContext.getCommandData());
                    }
                    break;
            }
        }
        DownloadData.deleteOtherOfThisNote(execContext.getMyContext(), noteId,
                downloads.stream().map(DownloadData::getDownloadId).collect(Collectors.toList()));
    }

    private void renumber() {
        List<Attachment> copy = new ArrayList<>(list);
        Collections.sort(copy);
        for (int ind = 0; ind < copy.size(); ind++) {
            copy.get(ind).downloadNumber = ind;
        }
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void clear() {
        list.clear();
    }

    public Attachments copy() {
        Attachments attachments = new Attachments();
        attachments.list.addAll(list);
        return attachments;
    }

    public int size() {
        return list.size();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                list +
                '}';
    }

    public static Attachments load(MyContext myContext, long noteId) {
        if (myContext.isEmptyOrExpired() || noteId == 0) return Attachments.EMPTY;

        NoteDownloads downloads = NoteDownloads.fromNoteId(myContext, noteId);
        if (downloads.isEmpty()) return Attachments.EMPTY;

        Map<Long, Attachment> map = new HashMap<>();
        for (DownloadData downloadData : downloads.list) {
            map.put(downloadData.getDownloadId(), new Attachment(downloadData));
        }

        Attachments attachments = new Attachments();
        for (DownloadData downloadData : downloads.list) {
            Attachment attachment = map.get(downloadData.getDownloadId());
            if (downloadData.getPreviewOfDownloadId() != 0) {
                Attachment target = map.get(downloadData.getPreviewOfDownloadId());
                if (target == null) {
                    MyLog.e(downloadData, "Couldn't find target of preview " + downloadData);
                } else {
                    attachment.setPreviewOf(target);
                }
            }
            attachments = attachments.add(attachment);
        }
        return attachments;
    }

    public long toUploadCount() {
        return list.stream().filter(a -> !UriUtils.isDownloadable(a.uri)).count();
    }

    public Attachment getFirstToUpload() {
        return list.stream().filter(a -> !UriUtils.isDownloadable(a.uri)).findFirst().orElse(Attachment.EMPTY);
    }
}
