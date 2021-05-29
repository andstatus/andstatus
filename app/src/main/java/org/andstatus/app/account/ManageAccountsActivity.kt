/*
 * Copyright (c) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.account

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.andstatus.app.FirstActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.util.MyUrlSpan
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @author yvolk@yurivolkov.com
 * Dragging implementation started from https://github.com/yfujiki/Android-DragReorderSample
 */
class ManageAccountsActivity : MyActivity() {

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP
                or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0) {

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val adapter = recyclerView.adapter as MyRecyclerViewAdapter
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                adapter.notifyItemMoved(from, to)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.manage_accounts
        super.onCreate(savedInstanceState)
        supportActionBar?.setTitle(R.string.manage_accounts)

        findViewById<RecyclerView>(android.R.id.list)?.let { recyclerView ->
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = MyRecyclerViewAdapter(this)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }
    }

    override fun onResume() {
        super.onResume()
        if ( MyContextHolder.myContextHolder.needToRestartActivity()) {
            FirstActivity.closeAllActivities(this)
             MyContextHolder.myContextHolder.initialize(this).thenStartActivity(intent)
        }
    }

    override fun onPause() {
        reorderAccounts()
        super.onPause()
    }

    fun reorderAccounts() {
        findViewById<RecyclerView>(android.R.id.list)?.let { recyclerView ->
            val adapter = recyclerView.adapter as MyRecyclerViewAdapter
            MyContextHolder.myContextHolder.getNow().accounts().reorderAccounts(adapter.accounts)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            MySettingsActivity.goToMySettingsAccounts(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

}

private class MyRecyclerViewAdapter(val activity: ManageAccountsActivity):
        RecyclerView.Adapter<MyRecyclerViewAdapter.MyRecyclerViewHolder>() {
    val accounts = CopyOnWriteArrayList(MyContextHolder.myContextHolder.getNow().accounts().get())
            .toMutableList()
    val syncedText = activity.getText(R.string.synced_abbreviated).toString()

    fun moveItem(from: Int, to: Int) {
        val fromEmoji = accounts[from]
        accounts.removeAt(from)
        accounts.add(to, fromEmoji)
    }

    override fun getItemCount(): Int {
        return accounts.size
    }

    override fun onBindViewHolder(holder: MyRecyclerViewHolder, position: Int) {
        val myAccount = accounts[position]
        holder.bind(activity, myAccount, syncedText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyRecyclerViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.drag_accountlist_item, parent, false)
        val viewHolder = MyRecyclerViewHolder(itemView)

        viewHolder.itemView.findViewById<View>(R.id.dragHandle)?.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                activity.startDragging(viewHolder)
            }
            return@setOnTouchListener true
        }

        return viewHolder
    }

    class MyRecyclerViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind(activity: ManageAccountsActivity, ma: MyAccount, syncedText: String) {
            val visibleName = ma.getAccountName().let { if(ma.isValidAndSucceeded()) it else "($it)" }
            MyUrlSpan.showText(itemView, R.id.visible_name, visibleName, false, true)
                    ?.setOnClickListener {
                        activity.reorderAccounts()
                        if (ma.myContext.isPreferencesChanged()) {
                            activity.finish()
                        }
                        val intent = Intent(activity, AccountSettingsActivity::class.java)
                        intent.putExtra(IntentExtra.ACCOUNT_NAME.key, ma.getAccountName())
                        activity.startActivity(intent)
                    }
            MyUrlSpan.showText(itemView, R.id.sync_auto, if (ma.isSyncedAutomatically()) syncedText else "",
                    false, true)
            itemView.tag = visibleName
        }
    }
}
