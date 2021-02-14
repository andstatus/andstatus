/*
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;

import androidx.annotation.NonNull;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorsScreenType;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListViewParameters;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.view.MyContextMenu;

public enum NoteContextMenuItem implements ContextMenuItem {
    REPLY(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .addMentionsToText()
                    .copySensitiveProperty();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    EDIT(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.load(menu.getMyContext(), menu.getNoteId());
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    RESEND(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            MyAccount ma = menu.getMyContext().accounts().fromActorId(
                    MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, menu.getNoteId()));
            long activityId = MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, menu.getNoteId());
            CommandData commandData = CommandData.newUpdateStatus(ma, activityId, menu.getNoteId());
            MyServiceManager.sendManualForegroundCommand(commandData);
            return null;
        }
    },
    REPLY_TO_CONVERSATION_PARTICIPANTS(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .setReplyToConversationParticipants(true)
                    .addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    REPLY_TO_MENTIONED_ACTORS(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .setReplyToMentionedActors(true)
                    .addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    LIKE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendNoteCommand(CommandEnum.LIKE, editorData);
        }
    },
    UNDO_LIKE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendNoteCommand(CommandEnum.UNDO_LIKE, editorData);
        }
    },
    ANNOUNCE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendNoteCommand(CommandEnum.ANNOUNCE, editorData);
        }
    },
    UNDO_ANNOUNCE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendNoteCommand(CommandEnum.UNDO_ANNOUNCE, editorData);
        }
    },
    DELETE_NOTE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendNoteCommand(CommandEnum.DELETE_NOTE, editorData);
        }
    },
    SHARE(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia());
            noteShare.share(menu.getActivity());
            return NoteEditorData.EMPTY;
        }
    },
    COPY_TEXT(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            String body = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, menu.getNoteId());
            return NoteEditorData.newEmpty(menu.getActingAccount()).setContent(body, TextMediaType.HTML);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            copyNoteText(editorData);
        }
    },
    COPY_AUTHOR(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            final Actor author = Actor.load(menu.getMyContext(),
                    MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, menu.getNoteId()));
            MyLog.v(this, () -> "noteId:" + menu.getNoteId() + " -> author:" + author);
            return NoteEditorData.newEmpty(menu.getActingAccount()).appendMentionedActorToText(author);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            copyNoteText(editorData);
        }
    },
    NOTES_BY_ACTOR(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(MyAccount.EMPTY)
                    .setTimeline(menu.getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.SENT, menu.getActor()));
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.switchTimelineActivityView(editorData.timeline);
        }
    },
    NOTES_BY_AUTHOR(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(MyAccount.EMPTY)
                    .setTimeline(menu.getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.SENT, menu.getAuthor()));
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.switchTimelineActivityView(editorData.timeline);
        }
    },
    FOLLOW_ACTOR(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu.getActingAccount(), menu.getActor());
        }
    },
    UNDO_FOLLOW_ACTOR(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu.getActingAccount(), menu.getActor());
        }
    },
    FOLLOW_AUTHOR(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu.getActingAccount(), menu.getAuthor());
        }
    },
    UNDO_FOLLOW_AUTHOR(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu.getActingAccount(), menu.getAuthor());
        }
    },
    PROFILE(false, false),
    BLOCK,
    ACT_AS_FIRST_OTHER_ACCOUNT(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            MyAccount actingAccount = menu.getActingAccount();
            if (actingAccount.isValid()) {
                menu.setSelectedActingAccount(
                    menu.getMyContext().accounts()
                        .firstOtherSucceededForSameOrigin(menu.getOrigin(), actingAccount)
                );
                menu.showContextMenu();
            } else {
                ACT_AS.executeOnUiThread(menu, editorData);
            }
        }
    },
    ACT_AS(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            AccountSelector.selectAccountOfOrigin(menu.getActivity(),
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, menu.getOrigin().getId());
        }
    },
    OPEN_NOTE_PERMALINK {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia());
            noteShare.openPermalink(menu.getActivity());
        }
    },
    VIEW_MEDIA(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia());
            noteShare.viewImage(menu.getActivity());
        }
    },
    OPEN_CONVERSATION(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(
                    menu.getMyContext().timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, menu.getOrigin()),
                    menu.getNoteId());
            String action = menu.getActivity().getIntent().getAction();
            if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, setData=" + uri);
                }
                menu.getActivity().setResult(Activity.RESULT_OK, new Intent().setData(uri));
            } else {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, startActivity=" + uri);
                }
                menu.getActivity().startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri));
            }
        }
    },
    ACTORS_OF_NOTE(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Uri uri = MatchedUri.getActorsScreenUri(
                    ActorsScreenType.ACTORS_OF_NOTE, menu.getOrigin().getId(),
                    menu.getNoteId(), "");
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=" + uri);
            }
            menu.getActivity().startActivity(MyAction.VIEW_ACTORS.getIntent(uri));
        }
    },
    SHOW_DUPLICATES(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.getActivity().updateList(LoadableListViewParameters.collapseOneDuplicate(
                    false, menu.getViewItem().getTopmostId()));
        }
    },
    COLLAPSE_DUPLICATES(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.getActivity().updateList(LoadableListViewParameters.collapseOneDuplicate(
                    true, menu.getViewItem().getTopmostId()));
        }
    },
    GET_NOTE(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            DownloadStatus status = DownloadStatus.load(
                    MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, menu.getNoteId()));
            if (status == DownloadStatus.LOADED) {
                MyProvider.update(menu.getMyContext(), NoteTable.TABLE_NAME,
                        NoteTable.NOTE_STATUS + "=" + DownloadStatus.NEEDS_UPDATE.save(),
                        NoteTable._ID + "=" + menu.getNoteId());
            }
            return super.executeAsync(menu);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Note.requestDownload(menu.getActingAccount(), menu.getNoteId(), true);
        }
    },
    OPEN_NOTE_LINK {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare.openLink(menu.getActivity(), extractUrlFromTitle(menu.getSelectedMenuItemTitle()));
        }

        private String extractUrlFromTitle(@NonNull String title) {
            int ind = title.indexOf(NOTE_LINK_SEPARATOR);
            if (ind < 0) {
                return title;
            }
            return title.substring(ind + NOTE_LINK_SEPARATOR.length());
        }
    },
    NONEXISTENT,
    UNKNOWN;

    public static final String NOTE_LINK_SEPARATOR = ": ";
    private static final String TAG = NoteContextMenuItem.class.getSimpleName();
    private final boolean mIsAsync;
    public final boolean appliedToUnsentNotesAlso;

    NoteContextMenuItem() {
        this(false, false);
    }

    NoteContextMenuItem(boolean isAsync, boolean appliedToUnsentNotesAlso) {
        this.mIsAsync = isAsync;
        this.appliedToUnsentNotesAlso = appliedToUnsentNotesAlso;
    }

    @Override
    public int getId() {
        return Menu.FIRST + ordinal() + 1;
    }
    
    public static NoteContextMenuItem fromId(int id) {
        for (NoteContextMenuItem item : NoteContextMenuItem.values()) {
            if (item.getId() == id) {
                return item;
            }
        }
        return UNKNOWN;
    }

    protected void copyNoteText(NoteEditorData editorData) {
        MyLog.v(this, () -> "text='" + editorData.getContent() + "'");
        if (!StringUtil.isEmpty(editorData.getContent())) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) editorData.myContext.context().
                    getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(
                    I18n.trimTextAt(MyHtml.htmlToCompactPlainText(editorData.getContent()), 40),
                    MyHtml.htmlToPlainText(editorData.getContent()));
            clipboard.setPrimaryClip(clip);
            MyLog.v(this, () -> "clip='" + clip.toString() + "'");
        }
    }

    public void addTo(Menu menu, int order, int titleRes) {
        menu.add(MyContextMenu.MENU_GROUP_NOTE, this.getId(), order, titleRes);
    }

    public void addTo(Menu menu, int order, CharSequence title) {
        menu.add(MyContextMenu.MENU_GROUP_NOTE, this.getId(), order, title);
    }
    
    public boolean execute(NoteContextMenu menu) {
        MyLog.v(this, "execute started");
        if (mIsAsync) {
            executeAsync1(menu);
        } else {
            MyAccount myAccount = menu.getActingAccount().getValidOrCurrent(menu.menuContainer.getActivity().getMyContext());
            NoteEditorData data = new NoteEditorData(myAccount, menu.getNoteId(), false, 0, false);
            executeOnUiThread(menu, data);
        }
        return false;
    }
    
    private void executeAsync1(final NoteContextMenu menu) {
        AsyncTaskLauncher.execute(TAG,
                new MyAsyncTask<Void, Void, NoteEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected NoteEditorData doInBackground2(Void aVoid) {
                        MyLog.v(NoteContextMenuItem.this, () ->
                                "execute async started. noteId=" + menu.getNoteId());
                        return executeAsync(menu);
                    }

                    @Override
                    protected void onPostExecute2(NoteEditorData editorData) {
                        MyLog.v(NoteContextMenuItem.this, "execute async ended");
                        executeOnUiThread(menu, editorData);
                    }
                }
        );
    }

    NoteEditorData executeAsync(NoteContextMenu menu) {
        return NoteEditorData.newEmpty(menu.getActingAccount());
    }

    void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
        // Empty
    }
    
    void sendActOnActorCommand(CommandEnum command, MyAccount myAccount, Actor actor) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.actOnActorCommand(command, myAccount, actor, actor.getUsername()));
    }

    void sendNoteCommand(CommandEnum command, NoteEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getNoteId()));
    }
}
