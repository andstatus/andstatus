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
package org.andstatus.app.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.woxthebox.draglistview.DragItem
import com.woxthebox.draglistview.DragItemAdapter
import com.woxthebox.draglistview.DragListView
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.account.AccountListFragment.ItemAdapter.AccountViewHolder
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.MyResources
import org.andstatus.app.util.MyUrlSpan
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @author yvolk@yurivolkov.com
 */
class AccountListFragment : Fragment() {
    private var mItems: MutableList<MyAccount>? = null
    private var mDragListView: DragListView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.drag_list_layout, container, false)
        mDragListView = view.findViewById(R.id.drag_list_view)
        mDragListView?.recyclerView?.isVerticalScrollBarEnabled = true
        mItems = CopyOnWriteArrayList<MyAccount>( MyContextHolder.myContextHolder.getNow().accounts().get())
        setupListRecyclerView()
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = activity as MyActivity?
        if (activity != null) {
            val actionBar = activity.supportActionBar
            actionBar?.setTitle(R.string.manage_accounts)
        }
    }

    override fun onPause() {
        super.onPause()
        mItems?.let {
            MyContextHolder.myContextHolder.getNow().accounts().reorderAccounts(it)
        }
    }

    private fun setupListRecyclerView() {
        mDragListView?.setLayoutManager(LinearLayoutManager(context))
        val listAdapter = ItemAdapter(mItems, R.layout.drag_accountlist_item, R.id.dragHandle)
        mDragListView?.setAdapter(listAdapter, true)
        mDragListView?.setCanDragHorizontally(false)
        mDragListView?.setCustomDragItem(MyDragItem(context, R.layout.drag_accountlist_item))
    }

    private class MyDragItem(context: Context?, layoutId: Int) : DragItem(context, layoutId) {
        override fun onBindDragView(clickedView: View, dragView: View) {
            val text = (clickedView.findViewById<View?>(R.id.visible_name) as TextView).text
            (dragView.findViewById<View?>(R.id.visible_name) as TextView).text = text
            dragView.setBackgroundColor(
                    MyResources.getColorByAttribute(
                            R.attr.actionBarColorAccent, android.R.attr.colorAccent, dragView.getContext().theme))
        }
    }

    internal inner class ItemAdapter(list: MutableList<MyAccount>?, private val mLayoutId: Int, private val mGrabHandleId: Int) : DragItemAdapter<MyAccount, AccountViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
            val view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false)
            return AccountViewHolder(view, mGrabHandleId)
        }

        override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val ma = mItemList?.get(position) ?: return

            var visibleName = ma.getAccountName()
            if (!ma.isValidAndSucceeded()) {
                visibleName = "($visibleName)"
            }
            MyUrlSpan.showText(holder.itemView, R.id.visible_name, visibleName, false, true)
            MyUrlSpan.showText(holder.itemView, R.id.sync_auto,
                    if (ma.isSyncedAutomatically() && ma.isValidAndSucceeded()) getText(R.string.synced_abbreviated).toString() else "", false, true)
            holder.itemView.tag = visibleName
        }

        override fun getUniqueItemId(position: Int): Long {
            return mItemList?.get(position)?.actorId ?: 0
        }

        internal inner class AccountViewHolder(itemView: View, grabHandleId: Int) :
                ViewHolder(itemView, grabHandleId, false) {
            override fun onItemClicked(view: View?) {
                val intent = Intent(activity, AccountSettingsActivity::class.java)
                intent.putExtra(IntentExtra.ACCOUNT_NAME.key,
                         MyContextHolder.myContextHolder.getNow().accounts().fromActorId(mItemId).getAccountName())
                activity?.startActivity(intent)
            }

            override fun onItemLongClicked(view: View?): Boolean {
                return true
            }
        }

        init {
            setHasStableIds(true)
            itemList = list
        }
    }

    companion object {
        fun newInstance(): ListFragment {
            return ListFragment()
        }
    }
}