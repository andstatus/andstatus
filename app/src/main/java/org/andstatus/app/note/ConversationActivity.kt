/* 
 * Copyright (c) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.ConversationViewItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.QueueViewer
import org.andstatus.app.timeline.BaseTimelineAdapter
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.timeline.LoadableListPosition
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.BundleUtils
import org.andstatus.app.util.MyCheckBox
import org.andstatus.app.util.MyStringBuilder

/**
 * One selected note and, optionally, the whole conversation
 *
 * @author yvolk@yurivolkov.com
 */
class ConversationActivity : NoteEditorListActivity<Any?>(), NoteContextMenuContainer {
    private var mContextMenu: NoteContextMenu? = null
    var mDrawerLayout: DrawerLayout? = null
    var mDrawerToggle: ActionBarDrawerToggle? = null
    private var showThreadsOfConversation = false
    private var oldNotesFirstInConversation = false
    private var origin: Origin? = Origin.Companion.EMPTY
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.conversation
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        origin = parsedUri.getOrigin(getMyContext())
        mContextMenu = NoteContextMenu(this)
        showThreadsOfConversation = MyPreferences.isShowThreadsOfConversation()
        oldNotesFirstInConversation = MyPreferences.areOldNotesFirstInConversation()
        MyCheckBox.setEnabled(this, R.id.showSensitiveContentToggle, MyPreferences.isShowSensitiveContent())
        initializeDrawer()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
    }

    private fun initializeDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout)
        if (mDrawerLayout != null) {
            mDrawerToggle = object : ActionBarDrawerToggle(
                    this,
                    mDrawerLayout,
                    R.string.drawer_open,
                    R.string.drawer_close
            ) {}
            mDrawerLayout.addDrawerListener(mDrawerToggle)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (ActivityRequestCode.Companion.fromId(requestCode)) {
            ActivityRequestCode.SELECT_ACCOUNT_TO_ACT_AS -> if (resultCode == RESULT_OK) {
                val myAccount: MyAccount = MyContextHolder.Companion.myContextHolder.getNow().accounts().fromAccountName(
                        data.getStringExtra(IntentExtra.ACCOUNT_NAME.key))
                if (myAccount.isValid) {
                    mContextMenu.setSelectedActingAccount(myAccount)
                    mContextMenu.showContextMenu()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        mContextMenu.onContextItemSelected(item)
        return super.onContextItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        prepareDrawer()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun prepareDrawer() {
        val drawerView = findViewById<View?>(R.id.navigation_drawer) ?: return
        MyCheckBox.set(drawerView, R.id.showThreadsOfConversation,
                showThreadsOfConversation) { v: CompoundButton?, isChecked: Boolean -> onShowThreadsOfConversationChanged(v, isChecked) }
        MyCheckBox.set(drawerView, R.id.oldNotesFirstInConversation,
                oldNotesFirstInConversation) { v: CompoundButton?, isChecked: Boolean -> onOldNotesFirstInConversationChanged(v, isChecked) }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        when (item.getItemId()) {
            R.id.commands_queue_id -> startActivity(Intent(activity, QueueViewer::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return false
    }

    fun onShowThreadsOfConversationChanged(v: View?, isChecked: Boolean) {
        closeDrawer()
        showThreadsOfConversation = isChecked
        MyPreferences.setShowThreadsOfConversation(isChecked)
        updateList(LoadableListPosition.Companion.EMPTY)
    }

    fun onOldNotesFirstInConversationChanged(v: View?, isChecked: Boolean) {
        closeDrawer()
        oldNotesFirstInConversation = isChecked
        MyPreferences.setOldNotesFirstInConversation(isChecked)
        updateList(LoadableListPosition.Companion.EMPTY)
    }

    fun onShowSensitiveContentToggleClick(view: View?) {
        closeDrawer()
        MyPreferences.setShowSensitiveContent((view as CheckBox?).isChecked())
        updateList(LoadableListPosition.Companion.EMPTY)
    }

    private fun closeDrawer() {
        val mDrawerList = findViewById<ViewGroup?>(R.id.navigation_drawer)
        mDrawerLayout.closeDrawer(mDrawerList)
    }

    override fun getTimeline(): Timeline? {
        return myContext.timelines().get(TimelineType.EVERYTHING, Actor.Companion.EMPTY, origin)
    }

    private fun getListLoader(): ConversationLoader? {
        return loaded as ConversationLoader
    }

    override fun newSyncLoader(args: Bundle?): SyncLoader<*>? {
        return ConversationLoaderFactory().getLoader(ConversationViewItem.Companion.EMPTY,
                getMyContext(), origin, centralItemId, BundleUtils.hasKey(args, IntentExtra.SYNC.key))
    }

    override fun newListAdapter(): BaseTimelineAdapter<*>? {
        return ConversationAdapter(mContextMenu, origin, centralItemId, getListLoader().getList(),
                showThreadsOfConversation, oldNotesFirstInConversation)
    }

    override fun getCustomTitle(): CharSequence? {
        mSubtitle = ""
        val title = StringBuilder(getText(R.string.label_conversation))
        MyStringBuilder.Companion.appendWithSpace(title, ListScope.ORIGIN.timelinePreposition(myContext))
        MyStringBuilder.Companion.appendWithSpace(title, origin.getName())
        return title
    }
}