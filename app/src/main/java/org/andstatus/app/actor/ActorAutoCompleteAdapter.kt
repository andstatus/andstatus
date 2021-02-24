/*
 * Copyright (C) 2017-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import org.andstatus.app.R
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.graphics.AvatarView
import org.andstatus.app.note.NoteBodyTokenizer
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.CollectionsUtil
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan

class ActorAutoCompleteAdapter(myActivity: LoadableListActivity<*>, origin: Origin) : BaseAdapter(), Filterable {
    private val origin: Origin?
    private val myActivity: LoadableListActivity<*>?
    private val mInflater: LayoutInflater?
    private var mFilter: ArrayFilter? = null
    private var items = FilteredValues.EMPTY
    fun getOrigin(): Origin? {
        return origin
    }

    override fun getCount(): Int {
        return items.viewItems.size
    }

    override fun getItem(position: Int): ActorViewItem? {
        return items.viewItems.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View?
        view = convertView ?: mInflater.inflate(R.layout.actor_lookup, parent, false)
        val item = getItem(position)
        if (item == null || item.actor.isEmpty) {
            MyUrlSpan.Companion.showText(view, R.id.username,
                    myActivity.getText(R.string.nothing_in_the_loadable_list).toString(), false, true)
        } else {
            val username = item.getActor().uniqueName
            MyUrlSpan.Companion.showAsPlainText(view, R.id.username, username, true)
            MyUrlSpan.Companion.showAsPlainText(view, R.id.description,
                    I18n.trimTextAt(item.actor.summary, 80).toString(), false)
            showAvatar(view, item)
        }
        return view
    }

    private fun showAvatar(view: View?, item: ActorViewItem?) {
        val avatarView: AvatarView = view.findViewById(R.id.avatar_image)
        if (item == null) {
            avatarView.visibility = View.INVISIBLE
        } else {
            item.showAvatar(myActivity, avatarView)
        }
    }

    override fun getFilter(): Filter {
        if (mFilter == null) {
            mFilter = ArrayFilter()
        }
        return mFilter
    }

    private class FilteredValues private constructor(val matchGroupsOnly: Boolean, val referenceChar: String?, val viewItems: MutableList<ActorViewItem?>?) {
        companion object {
            val EMPTY: FilteredValues? = FilteredValues(false, "", emptyList())
        }
    }

    /**
     *
     * An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.
     */
    private inner class ArrayFilter : Filter() {
        override fun performFiltering(prefixWithReferenceChar: CharSequence?): FilterResults? {
            if (!origin.isValid() || prefixWithReferenceChar.isNullOrEmpty() || prefixWithReferenceChar.length < NoteBodyTokenizer.Companion.MIN_LENGHT_TO_SEARCH + 1) {
                val results = FilterResults()
                results.values = FilteredValues.EMPTY
                results.count = 0
                return results
            }
            val referenceChar = prefixWithReferenceChar.get(0)
            val prefixString = prefixWithReferenceChar.toString().substring(1)
            val matchGroupsOnly = origin.groupActorReferenceChar().map { c: Char? -> c == referenceChar }.orElse(false)
            val viewItems = loadFiltered(matchGroupsOnly, prefixString.toLowerCase())
            CollectionsUtil.sort(viewItems)
            val results = FilterResults()
            results.values = FilteredValues(matchGroupsOnly, referenceChar.toString(), viewItems)
            results.count = viewItems.size
            return results
        }

        private fun loadFiltered(matchGroupsOnly: Boolean, prefixString: String?): MutableList<ActorViewItem?>? {
            val loader: ActorsLoader = object : ActorsLoader(myActivity.getMyContext(), ActorsScreenType.ACTORS_AT_ORIGIN,
                    origin, 0, "") {
                override fun getSelection(): String {
                    return if (matchGroupsOnly) {
                        ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.id + " AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.GROUP_TYPE +
                                " IN (" + GroupType.GENERIC.id + ", " + GroupType.ACTOR_OWNED.id + ") AND " +
                                ActorTable.TABLE_NAME + "." + ActorTable.USERNAME + " LIKE '" + prefixString + "%'"
                    } else {
                        (ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.id + " AND "
                                + ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID + " LIKE '" + prefixString + "%'")
                    }
                }
            }
            loader.load(null)
            val filteredValues = loader.list
            for (viewItem in filteredValues) {
                MyLog.v(this) { "filtered: " + viewItem.actor }
            }
            return filteredValues
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            items = results.values as FilteredValues
            if (results.count > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence? {
            if (resultValue !is ActorViewItem) {
                return ""
            }
            val item = resultValue as ActorViewItem?
            return items.referenceChar +
                    if (item.getActor().isEmpty) "" else if (origin.isMentionAsWebFingerId() && !items.matchGroupsOnly) item.actor.uniqueName else item.actor.username
        }
    }

    init {
        this.origin = origin
        this.myActivity = myActivity
        mInflater = LayoutInflater.from(myActivity)
    }
}