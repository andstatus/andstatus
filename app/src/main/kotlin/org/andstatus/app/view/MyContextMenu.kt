/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.view

import android.view.Menu
import android.view.View
import android.view.View.OnCreateContextMenuListener
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.timeline.EmptyViewItem
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.timeline.ViewItem
import org.andstatus.app.util.MyLog
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
abstract class MyContextMenu(protected val listActivity: LoadableListActivity<*>, val menuGroup: Int) : OnCreateContextMenuListener {
    private var viewOfTheContext: View? = null
    protected var mViewItem: ViewItem<*> = EmptyViewItem.EMPTY

    /**
     * Corresponding account information ( "Reply As..." ... )
     * oh whose behalf we are going to execute an action on this line in the list (on a note / other actor...)
     */
    @Volatile
    private var selectedActingAccount: MyAccount = MyAccount.EMPTY

    fun saveContextOfSelectedItem(v: View) {
        viewOfTheContext = v
        val viewItem = if (menuGroup == MENU_GROUP_ACTOR_PROFILE) listActivity.getListData().getActorViewItem()
            else listActivity.saveContextOfSelectedItem(v)
        if (viewItem.isEmpty || mViewItem.isEmpty || mViewItem.getId() != viewItem.getId()) {
            selectedActingAccount = MyAccount.EMPTY
        }
        mViewItem = viewItem
    }

    fun getActivity(): LoadableListActivity<*> {
        return listActivity
    }

    fun showContextMenu() {
        viewOfTheContext?.takeIf { it.getParent() != null && listActivity.isMyResumed() }
                ?.let { view ->
                    view.post(object : Runnable {
                        override fun run() {
                            try {
                                listActivity.openContextMenu(view)
                            } catch (e: NullPointerException) {
                                MyLog.d(this, "on showContextMenu", e)
                            }
                        }
                    })
                }
    }

    open fun getActingAccount(): MyAccount {
        return getSelectedActingAccount()
    }

    fun getSelectedActingAccount(): MyAccount {
        return selectedActingAccount
    }

    open fun setSelectedActingAccount(myAccount: MyAccount) {
        Objects.requireNonNull(myAccount)
        selectedActingAccount = myAccount
    }

    fun getMyContext(): MyContext {
        return getActivity().myContext
    }

    companion object {
        const val MENU_GROUP_ACTOR = Menu.FIRST
        const val MENU_GROUP_NOTE = Menu.FIRST + 1
        const val MENU_GROUP_OBJACTOR = Menu.FIRST + 2
        const val MENU_GROUP_ACTOR_PROFILE = Menu.FIRST + 3
    }
}
