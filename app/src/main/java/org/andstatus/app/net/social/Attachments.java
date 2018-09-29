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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Attachments implements IsEmpty {
    public final List<Attachment> list = new ArrayList<>();

    private void renumber() {
        Collections.sort(list);
        for (int ind = 0; ind < list.size(); ind++) {
            list.get(ind).downloadNumber = ind;
        }
    }

    public void save(CommandExecutionContext execContext, long noteId) {
        renumber();
        List<Long> downloadIds = new ArrayList<>();
        for (Attachment attachment : list) {
            DownloadData dd = DownloadData.getThisForAttachment(noteId, attachment);
            dd.setDownloadNumber(attachment.downloadNumber);
            dd.saveToDatabase();
            downloadIds.add(dd.getDownloadId());
            switch (dd.getStatus()) {
                case LOADED:
                case HARD_ERROR:
                    break;
                default:
                    if (UriUtils.isDownloadable(dd.getUri())) {
                        if ((attachment.contentType == MyContentType.IMAGE ||
                                attachment.contentType == MyContentType.VIDEO)
                                && MyPreferences.getDownloadAndDisplayAttachedImages()) {
                            dd.requestDownload();
                        }
                    } else {
                        AttachmentDownloader.load(dd, execContext.getCommandData());
                    }
                    break;
            }
        }
        DownloadData.deleteOtherOfThisNote(execContext.getMyContext(), noteId, downloadIds);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void add(Attachment attachment) {
        if (list.contains(attachment)) return;
        list.add(attachment);
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
        return "Attachments{" +
                list +
                '}';
    }
}
