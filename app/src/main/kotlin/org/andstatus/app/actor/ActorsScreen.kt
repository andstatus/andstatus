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
package org.andstatus.app.actor

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.SearchObjects
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.NoteEditorListActivity
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.timeline.WhichPage
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.view.MyContextMenu
import kotlin.reflect.KClass

/**
 * List of actors for different contexts
 * e.g. "Actors of the note", "Followers of my account(s)" etc.
 * @author yvolk@yurivolkov.com
 */
open class ActorsScreen(clazz: KClass<*> = ActorsScreen::class) : NoteEditorListActivity<ActorViewItem>(clazz) {
    protected var actorsScreenType: ActorsScreenType = ActorsScreenType.UNKNOWN
    private var contextMenu: ActorContextMenu? = null
    protected var centralActor: Actor = Actor.EMPTY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        actorsScreenType = parsedUri.getActorsScreenType()
        contextMenu = ActorContextMenu(this, MyContextMenu.MENU_GROUP_OBJACTOR)
        centralActor = Actor.load(myContext, centralItemId)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.actors, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sync_menu_item -> syncWithInternet(true)
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    override fun onRefresh() {
        syncWithInternet(true)
    }

    open fun syncWithInternet(manuallyLaunched: Boolean) {
        val method = "syncWithInternet"
        if (parsedUri.isSearch()) {
            showSyncing(method, getText(R.string.options_menu_sync))
            MyServiceManager.sendManualForegroundCommand(
                    CommandData.newSearch(SearchObjects.ACTORS, myContext,
                            parsedUri.getOrigin(myContext), parsedUri.searchQuery))
        } else {
            showList(WhichPage.CURRENT)
            hideSyncing(method)
        }
    }

    override fun newSyncLoader(args: Bundle?): ActorsLoader {
        return when (actorsScreenType) {
            ActorsScreenType.ACTORS_OF_NOTE -> ActorsOfNoteLoader(myContext, actorsScreenType, parsedUri.getOrigin(myContext),
                    centralItemId, parsedUri.searchQuery)
            else -> ActorsLoader(myContext, actorsScreenType, parsedUri.getOrigin(myContext),
                    centralItemId, parsedUri.searchQuery)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        contextMenu?.onContextItemSelected(item)
        return super.onContextItemSelected(item)
    }

    override fun newListAdapter(): BaseTimelineAdapter<ActorViewItem> {
        return ActorAdapter(contextMenu!!, R.layout.actor, getListLoader().getList(),
                Timeline.fromParsedUri(myContext, parsedUri, ""))
    }

    fun getListLoader(): ActorsLoader {
        return getLoaded() as ActorsLoader
    }

    override fun getCustomTitle(): CharSequence {
        mSubtitle = I18n.trimTextAt(MyHtml.htmlToCompactPlainText(getListLoader().getSubtitle()), 80)
        val title = MyStringBuilder()
        if (actorsScreenType.scope == ListScope.ORIGIN) {
            title.withSpace(actorsScreenType.title(this))
            val origin = parsedUri.getOrigin(myContext)
            if (origin.isValid) {
                title.withSpace(actorsScreenType.scope.timelinePreposition(myContext))
                title.withSpace(origin.name)
            }
        } else {
            val actor: Actor = centralActor
            if (actor.isEmpty) {
                title.withSpace(actorsScreenType.title(this))
            } else {
                title.withSpace(actorsScreenType.title(this, actor.actorNameInTimeline))
            }
        }
        if (parsedUri.searchQuery.isNotEmpty()) {
            title.withSpace("'" + parsedUri.searchQuery + "'")
        }
        return title.toString()
    }

    override fun isCommandToShowInSyncIndicator(commandData: CommandData): Boolean {
        return when (commandData.command) {
            CommandEnum.GET_ACTOR, CommandEnum.GET_FOLLOWERS, CommandEnum.GET_FRIENDS, CommandEnum.FOLLOW, CommandEnum.UNDO_FOLLOW, CommandEnum.SEARCH_ACTORS, CommandEnum.GET_AVATAR -> true
            else -> false
        }
    }

    override fun isRefreshNeededAfterExecuting(commandData: CommandData): Boolean {
        return when (commandData.command) {
            CommandEnum.FOLLOW, CommandEnum.UNDO_FOLLOW, CommandEnum.SEARCH_ACTORS -> true
            else -> super.isRefreshNeededAfterExecuting(commandData)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val method = "onActivityResult"
        MyLog.v(this) {
            (method + "; request:" + requestCode + ", result:"
                    + if (resultCode == RESULT_OK) "ok" else "fail")
        }
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS -> accountToActAsSelected(data)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun accountToActAsSelected(data: Intent) {
        val ma = myContext.accounts.fromAccountName(
                data.getStringExtra(IntentExtra.ACCOUNT_NAME.key))
        if (ma.isValid) {
            contextMenu?.setSelectedActingAccount(ma)
            contextMenu?.showContextMenu()
        }
    }

    init {
        mLayoutId = R.layout.my_list_swipe
    }
}
