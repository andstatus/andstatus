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

import org.andstatus.app.data.DownloadData;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Attachments implements IsEmpty {
    public final List<Attachment> list = new ArrayList<>();

    public void add(Attachment attachment) {
        if (!attachment.isValid() || list.contains(attachment)) return;
        list.add(attachment);
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
}
