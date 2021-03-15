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
package org.andstatus.app.note

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BaseColumns
import android.view.Menu
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.MyAction
import org.andstatus.app.account.AccountSelector
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Note
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.LoadableListViewParameters
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.view.MyContextMenu

enum class NoteContextMenuItem constructor(private val mIsAsync: Boolean = false, val appliedToUnsentNotesAlso: Boolean = false) : ContextMenuItem {
    REPLY(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .addMentionsToText()
                    .copySensitiveProperty()
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.menuContainer.getNoteEditor()?.startEditingNote(editorData)
        }
    },
    EDIT(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.load(menu.getMyContext(), menu.getNoteId())
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.menuContainer.getNoteEditor()?.startEditingNote(editorData)
        }
    },
    RESEND(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            val ma = menu.getMyContext().accounts().fromActorId(
                    MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, menu.getNoteId()))
            val activityId = MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, menu.getNoteId())
            val commandData: CommandData = CommandData.newUpdateStatus(ma, activityId, menu.getNoteId())
            MyServiceManager.sendManualForegroundCommand(commandData)
            return NoteEditorData.newEmpty(menu.getActingAccount())
        }
    },
    REPLY_TO_CONVERSATION_PARTICIPANTS(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .setReplyToConversationParticipants(true)
                    .addMentionsToText()
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.menuContainer.getNoteEditor()?.startEditingNote(editorData)
        }
    },
    REPLY_TO_MENTIONED_ACTORS(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.newReplyTo(menu.getNoteId(), menu.getActingAccount())
                    .setReplyToMentionedActors(true)
                    .addMentionsToText()
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.menuContainer.getNoteEditor()?.startEditingNote(editorData)
        }
    },
    LIKE {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendNoteCommand(CommandEnum.LIKE, editorData)
        }
    },
    UNDO_LIKE {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendNoteCommand(CommandEnum.UNDO_LIKE, editorData)
        }
    },
    ANNOUNCE {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendNoteCommand(CommandEnum.ANNOUNCE, editorData)
        }
    },
    UNDO_ANNOUNCE {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendNoteCommand(CommandEnum.UNDO_ANNOUNCE, editorData)
        }
    },
    DELETE_NOTE {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendNoteCommand(CommandEnum.DELETE_NOTE, editorData)
        }
    },
    SHARE(true, true) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            val noteShare = NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia())
            noteShare.share(menu.getActivity())
            return NoteEditorData.EMPTY
        }
    },
    COPY_TEXT(true, true) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            val body = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, menu.getNoteId())
            return NoteEditorData.newEmpty(menu.getActingAccount()).setContent(body, TextMediaType.HTML)
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            copyNoteText(editorData)
        }
    },
    COPY_AUTHOR(true, true) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            val author: Actor = Actor.load(menu.getMyContext(),
                    MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, menu.getNoteId()))
            MyLog.v(this) { "noteId:" + menu.getNoteId() + " -> author:" + author }
            return NoteEditorData.newEmpty(menu.getActingAccount()).appendMentionedActorToText(author)
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            copyNoteText(editorData)
        }
    },
    NOTES_BY_ACTOR(true, true) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.newEmpty(MyAccount.EMPTY)
                    .setTimeline(menu.getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.SENT, menu.getActor()))
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.switchTimelineActivityView(editorData.timeline)
        }
    },
    NOTES_BY_AUTHOR(true, true) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            return NoteEditorData.newEmpty(MyAccount.EMPTY)
                    .setTimeline(menu.getMyContext().timelines()
                            .forUserAtHomeOrigin(TimelineType.SENT, menu.getAuthor()))
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.switchTimelineActivityView(editorData.timeline)
        }
    },
    FOLLOW_ACTOR(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu.getActingAccount(), menu.getActor())
        }
    },
    UNDO_FOLLOW_ACTOR(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu.getActingAccount(), menu.getActor())
        }
    },
    FOLLOW_AUTHOR(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu.getActingAccount(), menu.getAuthor())
        }
    },
    UNDO_FOLLOW_AUTHOR(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu.getActingAccount(), menu.getAuthor())
        }
    },
    PROFILE(false, false), BLOCK, ACT_AS_FIRST_OTHER_ACCOUNT(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            val actingAccount = menu.getActingAccount()
            if (actingAccount.isValid) {
                menu.setSelectedActingAccount(
                        menu.getMyContext().accounts()
                                .firstOtherSucceededForSameOrigin(menu.getOrigin(), actingAccount)
                )
                menu.showContextMenu()
            } else {
                ACT_AS.executeOnUiThread(menu, editorData)
            }
        }
    },
    ACT_AS(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            AccountSelector.selectAccountOfOrigin(menu.getActivity(),
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, menu.getOrigin().id)
        }
    },
    OPEN_NOTE_PERMALINK {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            val noteShare = NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia())
            noteShare.openPermalink(menu.getActivity())
        }
    },
    VIEW_MEDIA(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            val noteShare = NoteShare(menu.getOrigin(), menu.getNoteId(), menu.getAttachedMedia())
            noteShare.viewImage(menu.getActivity())
        }
    },
    OPEN_CONVERSATION(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            val uri: Uri = MatchedUri.getTimelineItemUri(
                    menu.getMyContext().timelines()[TimelineType.EVERYTHING, Actor.EMPTY, menu.getOrigin()],
                    menu.getNoteId())
            val action = menu.getActivity().intent.action
            if (Intent.ACTION_PICK == action || Intent.ACTION_GET_CONTENT == action) {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, setData=$uri")
                }
                menu.getActivity().setResult(Activity.RESULT_OK, Intent().setData(uri))
            } else {
                if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                    MyLog.d(this, "onItemClick, startActivity=$uri")
                }
                menu.getActivity().startActivity(MyAction.VIEW_CONVERSATION.getIntent(uri))
            }
        }
    },
    ACTORS_OF_NOTE(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            val uri: Uri = MatchedUri.getActorsScreenUri(
                    ActorsScreenType.ACTORS_OF_NOTE, menu.getOrigin().id,
                    menu.getNoteId(), "")
            if (MyLog.isLoggable(this, MyLog.DEBUG)) {
                MyLog.d(this, "onItemClick, startActivity=$uri")
            }
            menu.getActivity().startActivity(MyAction.VIEW_ACTORS.getIntent(uri))
        }
    },
    SHOW_DUPLICATES(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.getActivity().updateList(LoadableListViewParameters.collapseOneDuplicate(
                    false, menu.getViewItem().getTopmostId()))
        }
    },
    COLLAPSE_DUPLICATES(false, true) {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            menu.getActivity().updateList(LoadableListViewParameters.collapseOneDuplicate(
                    true, menu.getViewItem().getTopmostId()))
        }
    },
    GET_NOTE(true, false) {
        override fun executeAsync(menu: NoteContextMenu): NoteEditorData {
            val status: DownloadStatus = DownloadStatus.load(
                    MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, menu.getNoteId()))
            if (status == DownloadStatus.LOADED) {
                MyProvider.update(menu.getMyContext(), NoteTable.TABLE_NAME,
                        NoteTable.NOTE_STATUS + "=" + DownloadStatus.NEEDS_UPDATE.save(),
                        BaseColumns._ID + "=" + menu.getNoteId())
            }
            return super.executeAsync(menu)
        }

        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            Note.requestDownload(menu.getActingAccount(), menu.getNoteId(), true)
        }
    },
    OPEN_NOTE_LINK {
        override fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
            NoteShare.openLink(menu.getActivity(), extractUrlFromTitle(menu.getSelectedMenuItemTitle()))
        }

        private fun extractUrlFromTitle(title: String): String {
            val ind = title.indexOf(NOTE_LINK_SEPARATOR)
            return if (ind < 0) {
                title
            } else title.substring(ind + NOTE_LINK_SEPARATOR.length)
        }
    },
    NONEXISTENT, UNKNOWN;

    override fun getId(): Int {
        return Menu.FIRST + ordinal + 1
    }

    protected fun copyNoteText(editorData: NoteEditorData) {
        MyLog.v(this) { "text='" + editorData.getContent() + "'" }
        if (editorData.getContent().isNotEmpty()) {
            // http://developer.android.com/guide/topics/text/copy-paste.html
            val clipboard = editorData.myContext.context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(
                    I18n.trimTextAt(MyHtml.htmlToCompactPlainText(editorData.getContent()), 40),
                    MyHtml.htmlToPlainText(editorData.getContent()))
            clipboard.setPrimaryClip(clip)
            MyLog.v(this) { "clip='$clip'" }
        }
    }

    fun addTo(menu: Menu, order: Int, titleRes: Int) {
        menu.add(MyContextMenu.MENU_GROUP_NOTE, this.getId(), order, titleRes)
    }

    fun addTo(menu: Menu, order: Int, title: CharSequence?) {
        menu.add(MyContextMenu.MENU_GROUP_NOTE, this.getId(), order, title)
    }

    fun execute(menu: NoteContextMenu): Boolean {
        MyLog.v(this, "execute started")
        if (mIsAsync) {
            executeAsync1(menu)
        } else {
            val myAccount = menu.getActingAccount().getValidOrCurrent(menu.menuContainer.getActivity().myContext)
            val data = NoteEditorData(myAccount, menu.getNoteId(), false, 0, false)
            executeOnUiThread(menu, data)
        }
        return false
    }

    private fun executeAsync1(menu: NoteContextMenu) {
        AsyncTaskLauncher.execute(TAG,
                object : MyAsyncTask<Void?, Void?, NoteEditorData>(TAG + name, PoolEnum.QUICK_UI) {
                    override fun doInBackground2(aVoid: Void?): NoteEditorData {
                        MyLog.v(this@NoteContextMenuItem) { "execute async started. noteId=" + menu.getNoteId() }
                        return executeAsync(menu)
                    }

                    override fun onPostExecute2(editorData: NoteEditorData) {
                        MyLog.v(this@NoteContextMenuItem, "execute async ended")
                        executeOnUiThread(menu, editorData)
                    }
                }
        )
    }

    open fun executeAsync(menu: NoteContextMenu): NoteEditorData {
        return NoteEditorData.newEmpty(menu.getActingAccount())
    }

    open fun executeOnUiThread(menu: NoteContextMenu, editorData: NoteEditorData) {
        // Empty
    }

    fun sendActOnActorCommand(command: CommandEnum, myAccount: MyAccount, actor: Actor) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.actOnActorCommand(command, myAccount, actor, actor.getUsername()))
    }

    fun sendNoteCommand(command: CommandEnum, editorData: NoteEditorData) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.newItemCommand(command, editorData.ma, editorData.getNoteId()))
    }

    companion object {
        val NOTE_LINK_SEPARATOR: String = ": "
        private val TAG: String = NoteContextMenuItem::class.java.simpleName
        fun fromId(id: Int): NoteContextMenuItem {
            for (item in values()) {
                if (item.getId() == id) {
                    return item
                }
            }
            return UNKNOWN
        }
    }
}