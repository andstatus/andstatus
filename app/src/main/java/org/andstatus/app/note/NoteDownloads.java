package org.andstatus.app.note;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.IsEmpty;

import java.util.List;

public class NoteDownloads implements IsEmpty {
    public final long noteId;
    public final List<DownloadData> list;

    private NoteDownloads(long noteId, List<DownloadData> list) {
        this.noteId = noteId;
        this.list = list;
    }

    public static NoteDownloads fromNoteId(MyContext myContext, long noteId) {
        return new NoteDownloads(noteId, DownloadData.fromNoteId(myContext, noteId));
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    public DownloadData getFirstForTimeline() {
        return list.stream()
            .filter(d -> d.getStatus() == DownloadStatus.LOADED && d.getContentType() == MyContentType.IMAGE)
            .findFirst()
            .orElse(
                list.stream().filter(d -> d.getStatus() == DownloadStatus.LOADED)
                    .findFirst()
                    .orElse(list.stream().findFirst().orElse(DownloadData.EMPTY)));
    }

    public DownloadData getFirstToShare() {
        return list.stream().filter(d -> d.getPreviewOfDownloadId() == 0).findFirst()
                .orElse(list.stream().findFirst().orElse(DownloadData.EMPTY));
    }

    public DownloadData fromId(long downloadId) {
        return list.stream().filter(d -> d.getDownloadId() == downloadId).findFirst().orElse(DownloadData.EMPTY);
    }
}
