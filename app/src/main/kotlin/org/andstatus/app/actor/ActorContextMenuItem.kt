/*
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.actor

import android.net.Uri
import android.view.Menu
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.MyAction
import org.andstatus.app.account.AccountSelector
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.list.ContextMenuItem
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncResult
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.TimelineActivity.Companion.startForTimeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog

enum class ActorContextMenuItem constructor(private val mIsAsync: Boolean = false) : ContextMenuItem {
    GET_ACTOR(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            params.menu.getViewItem().actor.requestDownload(true)
            return super.executeAsync(params)
        }
    },
    POST_TO(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            return Try.success(
                NoteEditorData.newEmpty(params.menu.getActingAccount())
                    .setPublicAndFollowers(false, false)
                    .addActorsBeforeText(mutableListOf(params.menu.getViewItem().actor))
            )
        }

        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            menu.menuContainer.getNoteEditor()?.startEditingNote(editorData)
        }
    },
    SHARE {
        // TODO
    },
    NOTES_BY_ACTOR(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            startForTimeline(
                    params.menu.getActivity().myContext,
                    params.menu.getActivity(),
                    params.menu.getActivity().myContext.timelines
                            .forUserAtHomeOrigin(TimelineType.SENT, params.menu.getViewItem().actor)
            )
            return super.executeAsync(params)
        }
    },
    LISTS(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            setActingAccountForActor(params)
            return super.executeAsync(params)
        }

        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            startGroupMembersScreen(menu, ActorsScreenType.LISTS)
        }
    },
    LIST_MEMBERS(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            setActingAccountForActor(params)
            return super.executeAsync(params)
        }

        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            startGroupMembersScreen(menu, ActorsScreenType.LIST_MEMBERS)
        }
    },
    GROUP_NOTES(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            startForTimeline(
                    params.menu.getActivity().myContext,
                    params.menu.getActivity(),
                    params.menu.getActivity().myContext.timelines
                            .forUserAtHomeOrigin(TimelineType.GROUP, params.menu.getViewItem().actor)
            )
            return super.executeAsync(params)
        }
    },
    FOLLOW {
        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.FOLLOW, menu)
        }
    },
    STOP_FOLLOWING {
        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            sendActOnActorCommand(CommandEnum.UNDO_FOLLOW, menu)
        }
    },
    ACT_AS_FIRST_OTHER_ACCOUNT {
        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            menu.setSelectedActingAccount(menu.getMyContext().accounts
                    .firstOtherSucceededForSameUser(menu.getViewItem().actor, menu.getActingAccount()))
            menu.showContextMenu()
        }
    },
    ACT_AS {
        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            AccountSelector.selectAccountForActor(menu.getActivity(), menu.menuGroup,
                    ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS, menu.getViewItem().actor)
        }
    },
    FOLLOWERS(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            setActingAccountForActor(params)
            return super.executeAsync(params)
        }

        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            startGroupMembersScreen(menu, ActorsScreenType.FOLLOWERS)
        }
    },
    FRIENDS(true) {
        override fun executeAsync(params: Params): Try<NoteEditorData> {
            setActingAccountForActor(params)
            return super.executeAsync(params)
        }

        override fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
            startGroupMembersScreen(menu, ActorsScreenType.FRIENDS)
        }
    },
    NONEXISTENT, UNKNOWN;

    class Params(var menu: ActorContextMenu)

    override fun getId(): Int {
        return Menu.FIRST + ordinal + 1
    }

    fun addTo(menu: Menu, menuGroup: Int, order: Int, titleRes: Int) {
        menu.add(menuGroup, this.getId(), order, titleRes)
    }

    fun addTo(menu: Menu, menuGroup: Int, order: Int, title: CharSequence) {
        menu.add(menuGroup, this.getId(), order, title)
    }

    fun execute(menu: ActorContextMenu): Boolean {
        val params1 = Params(menu)
        MyLog.v(this, "execute started")
        if (mIsAsync) {
            AsyncResult<Params, NoteEditorData>(TAG + name, AsyncEnum.QUICK_UI)
                .doInBackground {
                    MyLog.v(this, "execute async started. " +
                                it.menu.getViewItem().actor.getUniqueNameWithOrigin())
                    executeAsync(it)
                }
                .onPostExecute { params, result ->
                    MyLog.v(this, "execute async ended")
                    result.onSuccess {
                        executeOnUiThread(params.menu, it)
                    }
                }
                .execute(TAG, params1)
        } else {
            val myAccount = menu.getActingAccount().getValidOrCurrent(menu.getMyContext())
            val editorData = NoteEditorData(myAccount, 0, false, 0, false)
            executeOnUiThread(params1.menu, editorData)
        }
        return false
    }

    open fun executeAsync(params: Params): Try<NoteEditorData> {
        return Try.success(NoteEditorData.newEmpty(params.menu.getActingAccount()))
    }

    open fun executeOnUiThread(menu: ActorContextMenu, editorData: NoteEditorData) {
        // Empty
    }

    fun setActingAccountForActor(params: Params) {
        val actor = params.menu.getViewItem().actor
        val origin = params.menu.getOrigin()
        if (!origin.isValid) {
            MyLog.w(this, "Unknown origin for " + params.menu.getViewItem().actor)
            return
        }
        if (params.menu.getActingAccount().nonValid || params.menu.getActingAccount().origin != origin) {
            params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts
                    .fromActorOfSameOrigin(actor))
            if (params.menu.getActingAccount().nonValid) {
                params.menu.setSelectedActingAccount(params.menu.getMyContext().accounts.getFirstPreferablySucceededForOrigin(origin))
            }
        }
    }

    fun startGroupMembersScreen(menu: ActorContextMenu, actorsScreenType: ActorsScreenType) {
        val uri: Uri = MatchedUri.getActorsScreenUri(
                actorsScreenType,
                menu.getOrigin().id,
                menu.getViewItem().getActorId(), "")
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "startGroupMembersScreen; $actorsScreenType, uri:$uri")
        }
        menu.getActivity().startActivity(MyAction.VIEW_GROUP_MEMBERS.newIntent(uri))
    }

    fun sendActOnActorCommand(command: CommandEnum, menu: ActorContextMenu) {
        MyServiceManager.sendManualForegroundCommand(
                CommandData.actOnActorCommand(command, menu.getActingAccount(),
                        menu.getViewItem().actor, menu.getViewItem().actor.getUsername()))
    }

    companion object {
        private val TAG: String = ActorContextMenuItem::class.simpleName!!
        fun fromId(id: Int): ActorContextMenuItem {
            for (item in values()) {
                if (item.getId() == id) {
                    return item
                }
            }
            return UNKNOWN
        }
    }
}
