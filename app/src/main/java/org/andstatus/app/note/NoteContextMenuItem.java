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
import android.view.Menu;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.view.MyContextMenu;

public enum NoteContextMenuItem implements ContextMenuItem {
    REPLY(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newReply(menu.getActingAccount(), menu.getNoteId()).addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    EDIT(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.load(menu.menuContainer.getActivity().getMyContext(), menu.getNoteId());
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    RESEND(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            MyAccount ma = MyContextHolder.get().accounts().fromActorId(
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
            return NoteEditorData.newReply(menu.getActingAccount(), menu.getNoteId())
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
            return NoteEditorData.newReply(menu.getActingAccount(), menu.getNoteId())
                    .setReplyToMentionedActors(true)
                    .addMentionsToText();
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.menuContainer.getNoteEditor().startEditingNote(editorData);
        }
    },
    PRIVATE_NOTE(true, false) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            return NoteEditorData.newEmpty(menu.getActingAccount())
                    .addToAudience(menu.getAuthor()).setPublic(false);
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
    SHARE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.share(menu.getActivity());
        }
    },
    COPY_TEXT(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            String body = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, menu.getNoteId());
            if (menu.getOrigin().isHtmlContentAllowed()) {
                body = MyHtml.fromHtml(body);
            }
            return NoteEditorData.newEmpty(menu.getActingAccount()).setContent(body);
        }

        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            copyNoteText(editorData);
        }
    },
    COPY_AUTHOR(true, true) {
        @Override
        NoteEditorData executeAsync(NoteContextMenu menu) {
            final long authorId = MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, menu.getNoteId());
            MyLog.v(this, () -> "noteId:" + menu.getNoteId() + " -> authorId:" + authorId);
            return NoteEditorData.newEmpty(menu.getActingAccount()).appendMentionedActorToText(authorId);
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
                    .setTimeline(menu.getActivity().getMyContext().timelines()
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
                    .setTimeline(menu.getActivity().getMyContext().timelines()
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
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.openPermalink(menu.getActivity());
        }
    },
    VIEW_IMAGE(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            NoteShare noteShare = new NoteShare(menu.getOrigin(), menu.getNoteId(),
                    menu.getImageFilename());
            noteShare.viewImage(menu.getActivity());
        }
    },
    OPEN_CONVERSATION(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            Uri uri = MatchedUri.getTimelineItemUri(
                    Timeline.getTimeline(TimelineType.EVERYTHING, 0, menu.getOrigin()),
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
            Uri uri = MatchedUri.getActorListUri(
                    ActorListType.ACTORS_OF_NOTE, menu.getOrigin().getId(),
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
            menu.getActivity().updateList(TriState.FALSE, menu.getViewItem().getTopmostId());
        }
    },
    COLLAPSE_DUPLICATES(false, true) {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            menu.getActivity().updateList(TriState.TRUE, menu.getViewItem().getTopmostId());
        }
    },
    GET_NOTE {
        @Override
        void executeOnUiThread(NoteContextMenu menu, NoteEditorData editorData) {
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newItemCommand(CommandEnum.GET_NOTE, menu.getActingAccount(),
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
    public final boolean forUnsentAlso;

    NoteContextMenuItem() {
        this(false, false);
    }

    NoteContextMenuItem(boolean isAsync, boolean forUnsentAlso) {
        this.mIsAsync = isAsync;
        this.forUnsentAlso = forUnsentAlso;
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
        if (!StringUtils.isEmpty(editorData.getContent())) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            ClipboardManager clipboard = (ClipboardManager) MyContextHolder.get().context().
                    getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(I18n.trimTextAt(editorData.getContent(), 40),
                    editorData.getContent());
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
            executeOnUiThread(menu, new NoteEditorData(menu.menuContainer.getActivity().getMyContext(),
                    menu.getActingAccount(), menu.getNoteId(), 0, false));
        }
        return false;
    }
    
    private void executeAsync1(final NoteContextMenu menu) {
        AsyncTaskLauncher.execute(TAG, true,
                new MyAsyncTask<Void, Void, NoteEditorData>(TAG + name(), MyAsyncTask.PoolEnum.QUICK_UI) {
                    @Override
                    protected NoteEditorData doInBackground2(Void... params) {
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
                CommandData.actOnActorCommand(command, myAccount, actor.actorId, actor.getUsername()));
    }

    void sendNoteCommand(CommandEnum command, NoteEditorData editorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getNoteId()));
    }
}
