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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.view.MyContextMenu;

public enum NoteContextMenuItem implements ContextMenuItem {
    REPLY(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToNoteId(menu.getNoteId()).addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    EDIT(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.load(menu.getNoteId());
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    RESEND(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            MyAccount ma = MyContextHolder.get().accounts().fromActorId(
                    MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, menu.getNoteId()));
            CommandData commandData = CommandData.newUpdateStatus(ma, menu.getNoteId());
            MyServiceManager.sendManualForegroundCommand(commandData);
            return null;
        }
    },
    REPLY_TO_CONVERSATION_PARTICIPANTS(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToNoteId(menu.getNoteId()).setReplyToConversationParticipants(true).
                    addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    REPLY_TO_MENTIONED_ACTORS(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(menu.getMyActor()).
                    setInReplyToNoteId(menu.getNoteId()).setReplyToMentionedActors(true).
                    addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    PRIVATE_NOTE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData_unused) {
            NoteEditorData editorData = NoteEditorData.newEmpty(menu.getMyActor())
                    .addRecipientId(menu.getAuthorId()).setPrivate(TriState.TRUE);
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
    SHARE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.share(menu.getActivity());
        }
    },
    COPY_TEXT(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            String body = MyQuery.noteIdToStringColumnValue(NoteTable.BODY, menu.getNoteId());
            if (menu.getOrigin().isHtmlContentAllowed()) {
                body = MyHtml.fromHtml(body);
            }
            return NoteEditorData.newEmpty(menu.getMyActor()).setBody(body);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            copyNoteText(editorData);
        }
    },
    COPY_AUTHOR(true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            final long authorId = MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, menu.getNoteId());
            MyLog.v(this, "noteId:" + menu.getNoteId() + " -> authorId:" + authorId);
            return NoteEditorData.newEmpty(menu.getMyActor()).appendMentionedActorToText(authorId);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            copyNoteText(editorData);
        }
    },
    ACTOR_ACTIONS {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.switchTimelineActivityView(
                    Timeline.getTimeline(menu.getActivity().getMyContext(), 0, TimelineType.ACTOR,
                    null, menu.getActorId(), menu.getOrigin(), ""));
        }
    },
    AUTHOR_ACTIONS {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.switchTimelineActivityView(
                    Timeline.getTimeline(menu.getActivity().getMyContext(), 0, TimelineType.ACTOR,
                    null, menu.getAuthorId(), menu.getOrigin(), ""));
        }
    },
    FOLLOW_ACTOR {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActorCommand(CommandEnum.FOLLOW, menu.getOrigin(), menu.getActorId());
        }
    },
    UNDO_FOLLOW_ACTOR {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActorCommand(CommandEnum.UNDO_FOLLOW, menu.getOrigin(), menu.getActorId());
        }
    },
    FOLLOW_AUTHOR {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActorCommand(CommandEnum.FOLLOW, menu.getOrigin(), menu.getAuthorId());
        }
    },
    UNDO_FOLLOW_AUTHOR {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            sendActorCommand(CommandEnum.UNDO_FOLLOW, menu.getOrigin(), menu.getAuthorId());
        }
    },
    PROFILE,
    BLOCK,
    ACT_AS_FIRST_OTHER_ACCOUNT {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.setMyActor(editorData.ma.firstOtherAccountOfThisOrigin());
            menu.showContextMenu();
        }
    },
    ACT_AS {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            AccountSelector.selectAccount(menu.getActivity(),
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, editorData.ma.getOriginId());
        }
    },
    OPEN_NOTE_PERMALINK {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.openPermalink(menu.getActivity());
        }
    },
    VIEW_IMAGE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.viewImage(menu.getActivity());
        }
    },
    OPEN_CONVERSATION {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(
                    Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, menu.getOrigin()),
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
    ACTORS_OF_NOTE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Uri uri = MatchedUri.getActorListUri(editorData.ma.getActorId(),
                    ActorListType.ACTORS_OF_NOTE, menu.getOrigin().getId(),
                    menu.getNoteId(), "");
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=" + uri);
            }
            menu.getActivity().startActivity(MyAction.VIEW_ACTORS.getIntent(uri));
        }
    },
    SHOW_DUPLICATES {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.getActivity().updateList(TriState.FALSE, menu.getViewItem().getTopmostId(), false);
        }
    },
    COLLAPSE_DUPLICATES {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.getActivity().updateList(TriState.TRUE, menu.getViewItem().getTopmostId(), false);
        }
    },
    GET_NOTE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newItemCommand(CommandEnum.GET_NOTE, menu.getMyActor(),
                    menu.getNoteId())
            );
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

    NoteContextMenuItem() {
        this(false);
    }

    NoteContextMenuItem(boolean isAsync) {
        this.mIsAsync = isAsync;
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
        MyLog.v(this, "text='" + editorData.body + "'");
        if (!TextUtils.isEmpty(editorData.body)) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context().
                    getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(I18n.trimTextAt(editorData.body, 40), editorData.body);
            clipboard.setPrimaryClip(clip);
            MyLog.v(this, "clip='" + clip.toString() + "'");
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
            executeOnUiThread(menu, NoteEditorData.newEmpty(menu.getMyActor()).
                    setNoteId(menu.getNoteId()));
        }
        return false;
    }
    
    private void executeAsync1(final NoteContextMenu menu) {
        AsyncTaskLauncher.execute(TAG, true,
                new MyAsyncTask<Void, Void, NoteEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected NoteEditorData doInBackground2(Void... params) {
                        MyLog.v(NoteContextMenuItem.this,
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
        return NoteEditorData.newEmpty(menu.getMyActor());
    }

    void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
        // Empty
    }
    
    void sendActorCommand(CommandEnum command, Origin origin, long actorId) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newActorCommand(command, null, origin, actorId, ""));
    }

    void sendNoteCommand(CommandEnum command, NoteEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getNoteId()));
    }
}
